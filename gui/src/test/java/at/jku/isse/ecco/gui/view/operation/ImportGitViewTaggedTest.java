package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import at.jku.isse.ecco.service.llm.FeatureSuggestionLog;
import at.jku.isse.ecco.service.llm.ImportedFeatureTraces;
import at.jku.isse.ecco.service.llm.LlmFeatureSuggestionClient;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An import of the tagged commits only - the releases: the commits between are suggested but not
 * imported, and a feature any of them changed gets a new revision in the next tagged commit.
 */
public class ImportGitViewTaggedTest {

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

    // the build points the log into the build directory for all tests
    private String location;

    @BeforeEach
    public void saveLocation() {
        this.location = System.getProperty(FeatureSuggestionLog.LOCATION_PROPERTY);
    }

    @AfterEach
    public void restoreLocation() {
        if (this.location == null)
            System.clearProperty(FeatureSuggestionLog.LOCATION_PROPERTY);
        else
            System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, this.location);
    }

    /** The feature the stubbed LLM names for each commit; "fail" for none (the request failed). */
    private static final Map<String, String> SUGGESTED = Map.of("a", "A", "b", "B", "v1", "A", "a2", "A", "v2", "C", "after", "D");

    /** pre, v0 (tagged), a, b, v1 (tagged), a2, v2 (tagged), after - each adds a file named after it. */
    private static Path history(Path dir, Map<String, List<String>> tags) throws Exception {
        Files.createDirectories(dir);
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            for (String message : List.of("pre", "v0", "a", "b", "v1", "a2", "v2", "after")) {
                Files.writeString(dir.resolve(message + ".txt"), message + "\n");
                git.add().addFilepattern(".").call();
                String id = git.commit().setMessage(message).setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call().getId().name();
                if (message.startsWith("v")) {
                    git.tag().setName(message).setAnnotated(false).call();
                    tags.put(id, List.of(message));
                }
            }
        }
        return dir;
    }

    private static List<GitCommitInfo> oldestFirst(Path clone) {
        List<GitCommitInfo> commits = new ArrayList<>(new GitHistoryReader().listCommits(clone));
        Collections.reverse(commits);
        return commits;
    }

    private static ImportGitView start(EccoService service, Path clone, Map<String, List<String>> tags, List<GitCommitInfo> commits,
                                       boolean reviewEach, Set<String> failing, List<String> asked) throws Exception {
        return onFx(() -> {
            ImportGitView view = new ImportGitView(service);
            new Scene(view);
            view.suggester = commit -> {
                asked.add(commit.getMessage());
                if (failing.contains(commit.getMessage()))
                    return new ImportGitView.Suggested(new LlmFeatureSuggestionClient.SuggestionBatch(List.of(""), "the LLM is down"), "stub", 1);
                return new ImportGitView.Suggested(new LlmFeatureSuggestionClient.SuggestionBatch(List.of(SUGGESTED.get(commit.getMessage())), null), "stub", 1);
            };
            view.startTaggedImport(clone, tags, commits, true, reviewEach);
            return view;
        });
    }

    @Test
    @Timeout(90)
    public void onlyTaggedCommitsAreImportedWithTheRevisionsOfTheCommitsBetween(@TempDir Path tmp) throws Exception {
        Path log = tmp.resolve("suggestions.jsonl");
        System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, log.toString());
        Map<String, List<String>> tags = new LinkedHashMap<>();
        Path clone = history(tmp.resolve("clone"), tags);
        List<GitCommitInfo> commits = oldestFirst(clone);
        // the dialog leaves out what comes after the last tag: it would be suggested for no commit
        int last = ImportGitView.lastTagged(commits, tags);
        assertEquals(6, last);
        assertEquals(-1, ImportGitView.lastTagged(commits.subList(2, 4), tags));
        EccoService service = repository(tmp.resolve("repo"));
        List<String> asked = Collections.synchronizedList(new ArrayList<>());

        ImportGitView view = start(service, clone, tags, commits.subList(0, last + 1), false, Set.of(), asked);

        waitFor(view, FINISHED);
        // pre is part of BASE, v0 is BASE: neither is asked about
        assertEquals(List.of("a", "b", "v1", "a2", "v2"), asked);
        assertEquals(3, service.getCommits().size(), "v0, v1 and v2");
        String summary = onFx(view::summaryText);
        assertTrue(summary.startsWith("Imported 3 of 3 commits."), summary);
        assertTrue(summary.contains("4 commit(s) between the tagged ones were not imported"), summary);

        List<String> entries = Files.readAllLines(log);
        assertEquals(List.of("v0", "a", "b", "v1", "a2", "v2"), entries.stream().map(e -> field(e, "message")).toList());
        assertEquals(List.of("auto", "folded", "folded", "auto", "folded", "auto"), entries.stream().map(e -> field(e, "outcome")).toList());
        // A and B are new in v1, not revised; a2 revised A, so v2 has its new revision
        assertEquals(List.of("BASE", "BASE, A", "BASE, A, B", "BASE, A, B", "BASE, A', B", "BASE, A', B, C"),
                entries.stream().map(e -> field(e, "committed")).toList());
        Feature a = service.getRepository().getFeatures().stream().filter(f -> f.getName().equals("A")).findFirst().orElseThrow();
        assertEquals(2, a.getRevisions().size());

        // what the commits between changed counts for their features
        Map<String, Set<String>> files = ImportedFeatureTraces.fromLog(log, service.getRepositoryDir().toString(), clone, new GitHistoryReader(), null).files();
        assertEquals(Set.of("a.txt", "v1.txt", "a2.txt"), files.get("A"));
        assertEquals(Set.of("b.txt"), files.get("B"));
        assertEquals(Set.of("v2.txt"), files.get("C"));
        service.close();
    }

    /**
     * Only the tagged commits are reviewed, with the features of the commits since; one between
     * them stops only when its suggestion failed - and is then used for the next tagged commit, not
     * imported.
     */
    @Test
    @Timeout(90)
    public void taggedCommitsAreReviewedAndAFailedSuggestionBetweenStops(@TempDir Path tmp) throws Exception {
        System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, tmp.resolve("suggestions.jsonl").toString());
        Map<String, List<String>> tags = new LinkedHashMap<>();
        Path clone = history(tmp.resolve("clone"), tags);
        List<GitCommitInfo> commits = oldestFirst(clone);
        EccoService service = repository(tmp.resolve("repo"));

        ImportGitView view = start(service, clone, tags, commits.subList(0, 7), true, Set.of("b"), Collections.synchronizedList(new ArrayList<>()));

        waitFor(view, "Review Commit (2 of 7)");
        assertEquals("BASE", onFx(() -> findField(view).getText()));
        clickButton(view, "Import");
        // b's suggestion failed: it stops, and is used for the next tagged commit
        waitFor(view, "Review Commit (4 of 7)");
        assertNull(onFx(() -> find(view, n -> n instanceof Button button && button.getText().equals("Import"))), "b is not imported");
        onFx(() -> {
            findField(view).setText("BASE, A, B");
            return null;
        });
        clickButton(view, "Use for Next Tag");
        waitFor(view, "Review Commit (5 of 7)");
        assertEquals("BASE, A, B", onFx(() -> findField(view).getText()));
        clickButton(view, "Import");
        waitFor(view, "Review Commit (7 of 7)");
        assertEquals("BASE, A', B, C", onFx(() -> findField(view).getText()));
        clickButton(view, "Import");
        waitFor(view, FINISHED);
        assertEquals(3, service.getCommits().size());
        service.close();
    }

    /**
     * The commits between tagged ones are logged with the next tagged commit, when it is imported:
     * logged at once, those of a run stopped before it stayed in the log and counted for their
     * features, which were never committed - and a second run logged them again.
     */
    @Test
    @Timeout(90)
    public void commitsBetweenAreLoggedOnlyWithTheNextTaggedCommitImported(@TempDir Path tmp) throws Exception {
        Path log = tmp.resolve("suggestions.jsonl");
        System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, log.toString());
        Map<String, List<String>> tags = new LinkedHashMap<>();
        Path clone = history(tmp.resolve("clone"), tags);
        List<GitCommitInfo> commits = oldestFirst(clone);
        EccoService service = repository(tmp.resolve("repo"));

        // stopped at b, after a was folded: only v0 is logged
        ImportGitView view = start(service, clone, tags, commits.subList(0, 7), true, Set.of("b"), Collections.synchronizedList(new ArrayList<>()));
        waitFor(view, "Review Commit (2 of 7)");
        clickButton(view, "Import");
        waitFor(view, "Review Commit (4 of 7)");
        clickButton(view, "Stop");
        waitFor(view, FINISHED);
        assertEquals(List.of("v0"), Files.readAllLines(log).stream().map(e -> field(e, "message")).toList());
        assertNull(ImportedFeatureTraces.fromLog(log, service.getRepositoryDir().toString(), clone, new GitHistoryReader(), null).files().get("A"),
                "A was never committed");

        // the rest, again from a: logged once each, with v1 and v2
        ImportGitView second = start(service, clone, tags, commits.subList(2, 7), false, Set.of(), Collections.synchronizedList(new ArrayList<>()));
        waitFor(second, FINISHED);
        List<String> entries = Files.readAllLines(log);
        assertEquals(List.of("v0", "a", "b", "v1", "a2", "v2"), entries.stream().map(e -> field(e, "message")).toList());
        assertEquals(List.of("reviewed", "folded", "folded", "auto", "folded", "auto"), entries.stream().map(e -> field(e, "outcome")).toList());
        service.close();
    }

    @Test
    public void aTaggedCommitRevisesWhatTheCommitsSinceTheLastImportRevised() {
        // A imported and revised since, B imported and not, N new since: only imported ones get a new revision
        assertEquals("BASE, A', B, N", ImportGitView.releaseConfigurationText("BASE, A, B, N'", Set.of("A", "N"), Set.of("BASE", "A", "B")));
        assertEquals("BASE, A, B'", ImportGitView.releaseConfigurationText("BASE, A, B'", Set.of(), Set.of("BASE", "A", "B")));
        // the generic features keep one revision
        assertEquals("BASE, A', misc", ImportGitView.releaseConfigurationText("BASE, A, misc'", Set.of("A", "misc"), Set.of("BASE", "A", "misc")));
    }

    private static EccoService repository(Path dir) throws Exception {
        Files.createDirectories(dir);
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.init();
        return service;
    }

    /** the header a finished run shows (showSuccessHeader clears it) */
    private static final String FINISHED = "";

    private static void waitFor(ImportGitView view, String header) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
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

    private static TextField findField(ImportGitView view) {
        return (TextField) find(view, n -> n instanceof TextField);
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

    private static String field(String line, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + name + "\":(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^,}]*))").matcher(line);
        assertTrue(m.find(), name + " in " + line);
        return m.group(1) != null ? m.group(1).replace("\\/", "/") : m.group(2);
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
}
