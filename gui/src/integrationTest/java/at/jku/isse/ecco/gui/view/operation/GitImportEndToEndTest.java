package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.LlmPreferences;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static at.jku.isse.ecco.test.Shop.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-10 Import Git history (doc/requirements.md) as the product-line engineer runs it in the GUI's
 * import window: a Git clone of five commits, each reviewed, with the suggestions of an LLM - an
 * OpenAI-compatible endpoint over HTTP, as configured in Preferences, that answers like a model
 * would. The steps in the comments are those of the use case's flows. The window starts at the
 * selected range (B4): the folder is chosen in the system's own dialog, which a test cannot drive.
 * <p>
 * The oracle is the clone: the repository, opened again from disk, checks out each imported commit
 * as its Git tree, byte for byte; the clone is left as it was, byte for byte.
 */
public class GitImportEndToEndTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The Git history: message and tree, oldest first. */
    private static final List<Map.Entry<String, Map<String, byte[]>>> HISTORY = List.of(
            Map.entry("initial shop", BASE),
            Map.entry("add search", BASE_SEARCH),
            Map.entry("add export", BASE_SEARCH_EXPORT),
            Map.entry("add logging", BASE_SEARCH_EXPORT_LOGGING),
            Map.entry("reword the readme", withReadme(BASE_SEARCH_EXPORT_LOGGING, "# Shop\n\nA small online shop.\n")));

    /** What the LLM answers for a commit, by its message: the reply's content, or null for HTTP 500. */
    private static final Map<String, String> ANSWERS = Map.of(
            "add search", "{\"configuration\": \"search\", \"parents\": {\"search\": \"BASE\"}}",
            "add export", "{\"configuration\": \"export\", \"parents\": {\"export\": \"BASE\"}}",
            "reword the readme", "{\"configuration\": \"\"}");

    private final List<String> asked = new CopyOnWriteArrayList<>();
    private HttpServer llm;
    private String endpoint, model;

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    /** The LLM service: {@code POST /v1/chat/completions}, answering by the commit's summary line. */
    @BeforeEach
    public void startLlm() throws IOException {
        llm = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        llm.createContext("/v1/chat/completions", exchange -> {
            JsonNode request = JSON.readTree(exchange.getRequestBody());
            String user = request.path("messages").get(1).path("content").asText();
            asked.add(user);
            String summary = user.lines().filter(line -> line.startsWith("summary: ")).findFirst().orElse("").substring("summary: ".length());
            String content = ANSWERS.get(summary);
            byte[] reply;
            int status;
            if (content == null) {
                status = 500;
                reply = "model crashed".getBytes(StandardCharsets.UTF_8);
            } else {
                status = 200;
                reply = JSON.writeValueAsBytes(Map.of(
                        "choices", List.of(Map.of("message", Map.of("role", "assistant", "content", content))),
                        "usage", Map.of("prompt_tokens", 100, "completion_tokens", 10)));
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, reply.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(reply);
            }
        });
        llm.start();
        endpoint = LlmPreferences.getEndpointUrl();
        model = LlmPreferences.getModelName();
        LlmPreferences.setEndpointUrl("http://localhost:" + llm.getAddress().getPort());
        LlmPreferences.setModelName("stub-model");
    }

    @AfterEach
    public void stopLlm() {
        llm.stop(0);
        LlmPreferences.setEndpointUrl(endpoint);
        LlmPreferences.setModelName(model);
    }

    @Test
    @Timeout(300)
    public void aProductLineEngineerImportsAGitHistory(@TempDir Path tmp) throws Exception {
        Path clone = gitHistory(tmp.resolve("clone"));
        Map<String, byte[]> cloneBefore = tree(clone);
        Path repository = Files.createDirectories(tmp.resolve("repository"));
        EccoService service = new EccoService();
        service.setRepositoryDir(repository.resolve(".ecco"));
        service.init();

        // UC-10 B3-B4: the history along HEAD's first parents, oldest first; all of it, each
        // commit reviewed, with LLM suggestions
        List<GitCommitInfo> oldestFirst = new ArrayList<>(new GitHistoryReader().listCommits(clone));
        Collections.reverse(oldestFirst);
        assertEquals(HISTORY.stream().map(Map.Entry::getKey).toList(), oldestFirst.stream().map(GitCommitInfo::getMessage).map(String::strip).toList());
        ImportGitView view = onFx(() -> {
            ImportGitView v = new ImportGitView(service);
            new Scene(v);
            v.startImport(clone, oldestFirst, true, true);
            return v;
        });

        // UC-10 SA9: the first commit into an empty repository is BASE, without the LLM
        waitFor(view, "Review Commit (1 of 5)");
        assertEquals("BASE", configuration(view));
        assertTrue(asked.isEmpty(), "the LLM is not asked for the first commit");
        clickButton(view, "Import");

        // UC-10 B6-B15: the running feature set and the suggestion, imported as they are
        waitFor(view, "Review Commit (2 of 5)");
        assertEquals("BASE, search", configuration(view));
        clickButton(view, "Import");

        waitFor(view, "Review Commit (3 of 5)");
        assertEquals("BASE, search, export", configuration(view));
        // B8: the LLM is told the features imported so far
        assertTrue(asked.get(1).contains("- BASE") && asked.get(1).contains("- search"), asked.get(1));
        clickButton(view, "Import");

        // UC-10 SA3: the LLM fails; the running feature set and why, completed by hand
        waitFor(view, "Review Commit (4 of 5)");
        assertEquals("BASE, search, export", configuration(view));
        assertNotNull(onFx(() -> labelText(view, ImportGitView.SUGGESTION_FAILURE_LABEL_ID)), "the failed suggestion is shown");
        setConfiguration(view, "BASE, search, export, logging");
        clickButton(view, "Import");

        // UC-10 SA4: the last commit is skipped
        waitFor(view, "Review Commit (5 of 5)");
        clickButton(view, "Skip");

        // UC-10 B17: the summary
        waitFor(view, "");
        String summary = onFx(() -> ((javafx.scene.control.TextArea) find(view, n -> n instanceof javafx.scene.control.TextArea area
                && area.getText().startsWith("Imported "))).getText());
        assertTrue(summary.startsWith("Imported 4 of 5 commits, skipped 1."), summary);
        assertEquals(4, asked.size(), "the LLM is asked for every commit but the first");
        service.close();

        // UC-10 postcondition: the clone is unchanged...
        assertSameFiles(cloneBefore, tree(clone));
        // ...and the repository, opened again, holds one commit per imported Git commit, each
        // checking out as its tree
        try (EccoService reopened = new EccoService()) {
            reopened.setRepositoryDir(repository.resolve(".ecco"));
            reopened.open();
            List<Commit> commits = new ArrayList<>(reopened.getCommits());
            commits.sort(Comparator.comparing(Commit::getDate));
            assertEquals(4, commits.size());
            for (int i = 0; i < commits.size(); i++) {
                Path checkout = Files.createDirectories(tmp.resolve("checkout-" + i));
                reopened.setBaseDir(checkout);
                reopened.checkout(commits.get(i).getConfiguration().getConfigurationString());
                Map<String, byte[]> files = tree(checkout);
                files.keySet().removeAll(List.of(".config", ".warnings", ".hashes"));
                assertSameFiles(HISTORY.get(i).getValue(), files);
            }
        }
    }

    private static Map<String, byte[]> withReadme(Map<String, byte[]> variant, String readme) {
        Map<String, byte[]> files = new TreeMap<>(variant);
        files.put("README.md", readme.getBytes(StandardCharsets.UTF_8));
        return files;
    }

    /** The clone: one Git commit per entry of {@link #HISTORY}, its tree the entry's files. */
    private static Path gitHistory(Path dir) throws Exception {
        Files.createDirectories(dir);
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            for (Map.Entry<String, Map<String, byte[]>> commit : HISTORY) {
                for (Map.Entry<String, byte[]> file : commit.getValue().entrySet()) {
                    Path path = dir.resolve(file.getKey());
                    Files.createDirectories(path.getParent());
                    Files.write(path, file.getValue());
                }
                git.add().addFilepattern(".").call();
                git.commit().setMessage(commit.getKey()).setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
            }
        }
        return dir;
    }

    private static String configuration(ImportGitView view) throws Exception {
        return onFx(() -> ((TextField) find(view, n -> n instanceof TextField)).getText());
    }

    private static void setConfiguration(ImportGitView view, String configuration) throws Exception {
        onFx(() -> {
            ((TextField) find(view, n -> n instanceof TextField)).setText(configuration);
            return null;
        });
    }

    private static void waitFor(ImportGitView view, String header) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (onFx(() -> view.headerLabel.getText().equals(header)))
                return;
            Thread.sleep(50);
        }
        fail("timed out waiting for '" + header + "', header is '" + onFx(() -> view.headerLabel.getText()) + "'");
    }

    private static void clickButton(ImportGitView view, String text) throws Exception {
        onFx(() -> {
            Button button = (Button) find(view, n -> n instanceof Button b && b.getText().equals(text));
            assertNotNull(button, "button " + text);
            button.fire();
            return null;
        });
    }

    private static String labelText(ImportGitView view, String id) {
        Node label = find(view, n -> id.equals(n.getId()));
        return label == null ? null : ((Label) label).getText();
    }

    private static Node find(Node node, java.util.function.Predicate<Node> match) {
        if (match.test(node))
            return node;
        if (node instanceof javafx.scene.control.SplitPane split)
            for (Node item : split.getItems()) {
                Node found = find(item, match);
                if (found != null)
                    return found;
            }
        if (node instanceof javafx.scene.control.ToolBar bar)
            for (Node item : bar.getItems()) {
                Node found = find(item, match);
                if (found != null)
                    return found;
            }
        if (node instanceof Parent parent)
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = find(child, match);
                if (found != null)
                    return found;
            }
        return null;
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() instanceof Error error)
            throw error;
        if (failure.get() != null)
            throw new Exception(failure.get());
        return result.get();
    }

    private static Map<String, byte[]> tree(Path root) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).toList())
                files.put(root.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
        }
        return files;
    }

    private static void assertSameFiles(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String path : expected.keySet())
            assertArrayEquals(expected.get(path), actual.get(path), () -> path + " differs:\n"
                    + new String(actual.get(path), StandardCharsets.UTF_8));
    }
}
