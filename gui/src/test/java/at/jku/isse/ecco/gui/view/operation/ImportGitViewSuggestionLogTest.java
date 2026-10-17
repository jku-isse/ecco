package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import at.jku.isse.ecco.service.llm.FeatureSuggestionLog;
import at.jku.isse.ecco.service.llm.ImportedFeatureTraces;
import at.jku.isse.ecco.service.llm.LlmFeatureSuggestionClient;
import at.jku.isse.ecco.service.llm.SuggestionConfidence;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each LLM suggestion of a Git import is recorded with what became of it - imported unattended,
 * imported after review with the configuration left in the field, or skipped - so suggestions can
 * be scored against what was actually committed.
 */
public class ImportGitViewSuggestionLogTest {

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

    @Test
    @Timeout(60)
    public void eachSuggestionIsRecordedWithItsOutcome(@TempDir Path tmp) throws Exception {
        Path log = tmp.resolve("suggestions.jsonl");
        System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, log.toString());
        Path clone = gitHistory(tmp.resolve("clone"), "first", "second", "third", "fourth");
        EccoService service = repository(tmp.resolve("repo"));
        // reviews every 2nd commit: first and third are imported unattended
        ImportGitView view = startImport(service, clone, 2);

        waitFor(view, "Review Commit (2 of 4)");
        assertEquals("BASE, SECOND", onFx(() -> findField(view).getText()));
        onFx(() -> {
            findField(view).setText("BASE, SECOND, EXTRA");
            return null;
        });
        clickButton(view, "Import");
        waitFor(view, "Review Commit (4 of 4)");
        clickButton(view, "Skip");
        waitFor(view, "");
        service.close();

        List<String> entries = Files.readAllLines(log);
        assertEquals(List.of("first", "second", "third", "fourth"), entries.stream().map(e -> field(e, "message")).toList());
        assertEquals(List.of("auto", "reviewed", "auto", "skipped"), entries.stream().map(e -> field(e, "outcome")).toList());
        assertEquals(List.of("", "BASE", "BASE, SECOND, EXTRA", "BASE, SECOND, EXTRA, THIRD"), entries.stream().map(e -> field(e, "before")).toList());
        // the first commit into an empty repository is BASE, the LLM is not asked
        assertEquals(List.of("null", "SECOND", "THIRD", "FOURTH"), entries.stream().map(e -> field(e, "suggestion")).toList());
        assertEquals(List.of("BASE", "BASE, SECOND", "BASE, SECOND, EXTRA, THIRD", "BASE, SECOND, EXTRA, THIRD, FOURTH"),
                entries.stream().map(e -> field(e, "proposed")).toList());
        assertEquals(List.of("BASE", "BASE, SECOND, EXTRA", "BASE, SECOND, EXTRA, THIRD", "null"),
                entries.stream().map(e -> field(e, "committed")).toList());
        assertEquals("null", field(entries.get(0), "model"));
        String second = entries.get(1);
        assertEquals("stub", field(second, "model"));
        assertEquals("7", field(second, "diffLines"));
        assertEquals("100", field(second, "promptTokens"));
        assertEquals("42", field(second, "millis"));
        assertEquals(clone.toString(), field(second, "gitRepository"));
        assertEquals(service.getRepositoryDir().toString(), field(second, "eccoRepository"));

        // a later import run knows which files each feature's commits changed: a commit's
        // labels are the features it added and the suggested ones kept - a skipped one has none
        assertEquals(Map.of("BASE", Set.of("first.txt"), "SECOND", Set.of("second.txt"), "EXTRA", Set.of("second.txt"), "THIRD", Set.of("third.txt")),
                ImportedFeatureTraces.fromLog(log, service.getRepositoryDir().toString(), clone, new GitHistoryReader(), null).files());
        assertEquals(Map.of(), ImportedFeatureTraces.fromLog(log, "/another/.ecco", clone, new GitHistoryReader(), null).files(),
                "only the commits imported into this repository");
    }

    /**
     * The first commit into an empty repository is BASE, without asking the LLM; the parents the
     * LLM names for new features are logged, and a later suggestion is told them as the known
     * features' parents - not the tree's, which puts every feature of an import under the one before.
     */
    @Test
    @Timeout(60)
    public void theFirstCommitIsBaseAndTheNamedParentsAreLoggedAndTold(@TempDir Path tmp) throws Exception {
        Path log = tmp.resolve("suggestions.jsonl");
        System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, log.toString());
        Path clone = gitHistory(tmp.resolve("clone"), "first", "second", "third", "fourth");
        EccoService service = repository(tmp.resolve("repo"));
        List<GitCommitInfo> oldestFirst = new ArrayList<>(new GitHistoryReader().listCommits(clone));
        Collections.reverse(oldestFirst);
        List<String> asked = java.util.Collections.synchronizedList(new ArrayList<>());
        ImportGitView view = onFx(() -> {
            ImportGitView v = new ImportGitView(service);
            new Scene(v);
            v.suggester = commit -> {
                asked.add(commit.getMessage());
                String feature = commit.getMessage().toUpperCase();
                return new ImportGitView.Suggested(new LlmFeatureSuggestionClient.SuggestionBatch(List.of(feature), null, List.of(),
                        List.of(Map.of(feature, "BASE"))), "stub", 1);
            };
            // reviews every 4th commit: the first three are imported unattended
            v.startImport(clone, oldestFirst, true, 4);
            return v;
        });

        waitFor(view, "Review Commit (4 of 4)");
        assertEquals(List.of("second", "third", "fourth"), asked, "not asked for the first commit");
        List<String> entries = Files.readAllLines(log);
        assertEquals(List.of("BASE", "BASE, SECOND", "BASE, SECOND, THIRD"), entries.stream().map(e -> field(e, "committed")).toList());
        assertTrue(entries.get(1).contains("\"parents\":{\"SECOND\":\"BASE\"}"), entries.get(1));

        Map<String, String> told = new java.util.TreeMap<>();
        for (LlmFeatureSuggestionClient.KnownFeature known : view.suggestionInput(oldestFirst.get(3)).knownFeatures())
            told.put(known.name(), String.valueOf(known.parentName()));
        assertEquals(Map.of("BASE", "null", "SECOND", "BASE", "THIRD", "BASE"), told, "THIRD is not under SECOND");
        clickButton(view, "Skip");
        waitFor(view, FINISHED);
        service.close();
    }

    /** An unattended commit whose suggestion is uncertain stops for review, and says why; without the option it is imported. */
    @Test
    @Timeout(60)
    public void anUncertainSuggestionStopsForReview(@TempDir Path tmp) throws Exception {
        for (boolean reviewUncertain : new boolean[]{true, false}) {
            Path dir = Files.createDirectories(tmp.resolve("run-" + reviewUncertain));
            Path log = dir.resolve("suggestions.jsonl");
            System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, log.toString());
            Path clone = gitHistory(dir.resolve("clone"), "first", "second", "third");
            EccoService service = repository(dir.resolve("repo"));
            List<GitCommitInfo> oldestFirst = new ArrayList<>(new GitHistoryReader().listCommits(clone));
            Collections.reverse(oldestFirst);
            ImportGitView view = onFx(() -> {
                ImportGitView v = new ImportGitView(service);
                new Scene(v);
                v.reviewUncertain = reviewUncertain;
                v.suggester = commit -> new ImportGitView.Suggested(new LlmFeatureSuggestionClient.SuggestionBatch(
                        List.of(commit.getMessage().equals("first") ? "BASE" : commit.getMessage().toUpperCase()), null, List.of()), "stub", 1,
                        commit.getMessage().equals("third") ? new SuggestionConfidence(0.15, "BASE changed exactly these positions before") : null);
                // reviews every 2nd commit: first and third are imported unattended
                v.startImport(clone, oldestFirst, true, 2);
                return v;
            });
            waitFor(view, "Review Commit (2 of 3)");
            clickButton(view, "Import");
            if (reviewUncertain) {
                waitFor(view, "Review Commit (3 of 3)");
                assertEquals("Stopped for review - the suggestion is uncertain: BASE changed exactly these positions before.",
                        onFx(() -> ((javafx.scene.control.Label) find(view, n -> ImportGitView.CONFIDENCE_LABEL_ID.equals(n.getId()))).getText()));
                clickButton(view, "Import");
            }
            waitFor(view, "");
            service.close();

            String third = Files.readAllLines(log).get(2);
            assertEquals(reviewUncertain ? "reviewed" : "auto", field(third, "outcome"));
            assertEquals("0.15", field(third, "confidence"), "logged either way");
        }
    }

    /**
     * A suggestion request carries the commit's changes with their positions, and how they overlap
     * the known features' earlier changes - also those of commits imported in an earlier run, from the log.
     */
    @Test
    @Timeout(60)
    public void aSuggestionRequestCarriesTheChangedPositionsAndTheirOverlap(@TempDir Path tmp) throws Exception {
        Path log = tmp.resolve("suggestions.jsonl");
        System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, log.toString());
        Path clone = Files.createDirectories(tmp.resolve("clone"));
        try (Git git = Git.init().setDirectory(clone.toFile()).call()) {
            String[][] versions = {
                    {"first", "# Doc\n\n## Install\n\nrun it\n\n## Usage\n\nuse it\n"},
                    {"usage", "# Doc\n\n## Install\n\nrun it\n\n## Usage\n\nuse it well\n"},
                    {"again", "# Doc\n\n## Install\n\nrun it\n\n## Usage\n\nuse it very well\n"}};
            for (String[] version : versions) {
                Files.writeString(clone.resolve("README.md"), version[1]);
                git.add().addFilepattern(".").call();
                git.commit().setMessage(version[0]).setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
            }
        }
        EccoService service = repository(tmp.resolve("repo"));
        ImportGitView view = startImport(service, clone, 1);
        waitFor(view, "Review Commit (1 of 3)");
        clickButton(view, "Import");
        waitFor(view, "Review Commit (2 of 3)");
        clickButton(view, "Import");
        waitFor(view, "Review Commit (3 of 3)");

        List<GitCommitInfo> oldestFirst = new ArrayList<>(new GitHistoryReader().listCommits(clone));
        Collections.reverse(oldestFirst);
        ImportGitView.SuggestionInput input = view.suggestionInput(oldestFirst.get(2));
        service.close();

        assertEquals(Set.of("Doc > Usage"), input.commit().changes().get("README.md").positions());
        ImportedFeatureTraces.Overlap usage = input.commit().overlap().stream().filter(o -> o.feature().equals("USAGE")).findFirst().orElseThrow();
        assertEquals(List.of("Doc > Usage"), usage.samePositions());
        LlmFeatureSuggestionClient.KnownFeature known = input.knownFeatures().stream().filter(k -> k.name().equals("USAGE")).findFirst().orElseThrow();
        assertEquals(Set.of("README.md"), known.files());
        assertEquals(Set.of("Doc > Usage"), known.positions());
    }

    @Test
    @Timeout(60)
    public void commitsImportedWithoutSuggestionsAreRecordedToo(@TempDir Path tmp) throws Exception {
        Path log = tmp.resolve("suggestions.jsonl");
        System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, log.toString());
        Path clone = gitHistory(tmp.resolve("clone"), "first", "second");
        EccoService service = repository(tmp.resolve("repo"));
        List<GitCommitInfo> oldestFirst = new ArrayList<>(new GitHistoryReader().listCommits(clone));
        Collections.reverse(oldestFirst);
        ImportGitView view = onFx(() -> {
            ImportGitView v = new ImportGitView(service);
            new Scene(v);
            v.startImport(clone, oldestFirst, false, 1);
            return v;
        });
        waitFor(view, "Review Commit (1 of 2)");
        onFx(() -> {
            findField(view).setText("BASE");
            return null;
        });
        clickButton(view, "Import");
        waitFor(view, "Review Commit (2 of 2)");
        onFx(() -> {
            findField(view).setText("BASE, SECOND");
            return null;
        });
        clickButton(view, "Import");
        waitFor(view, "");
        service.close();

        List<String> entries = Files.readAllLines(log);
        assertEquals(List.of("null", "null"), entries.stream().map(e -> field(e, "model")).toList());
        assertEquals(List.of("BASE", "BASE, SECOND"), entries.stream().map(e -> field(e, "committed")).toList());
        assertEquals(Map.of("BASE", Set.of("first.txt"), "SECOND", Set.of("second.txt")),
                ImportedFeatureTraces.fromLog(log, service.getRepositoryDir().toString(), clone, new GitHistoryReader(), null).files());
    }

    /** a top-level field of one log line: a string's text, or a number or null as written */
    private static String field(String line, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + name + "\":(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^,}]*))").matcher(line);
        assertTrue(m.find(), name + " in " + line);
        return m.group(1) != null ? m.group(1).replace("\\/", "/") : m.group(2);
    }

    @Test
    public void diffLinesCountsWhatTheTruncationLeftOut() {
        assertEquals(3, ImportGitView.diffLines("a\nb\nc"));
        assertEquals(402, ImportGitView.diffLines("a\nb\n... (diff truncated, 400 more lines)"));
    }

    private static Path gitHistory(Path dir, String... messages) throws Exception {
        Files.createDirectories(dir);
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            for (String message : messages) {
                Files.writeString(dir.resolve(message + ".txt"), message + "\n");
                git.add().addFilepattern(".").call();
                git.commit().setMessage(message).setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
            }
        }
        return dir;
    }

    private static EccoService repository(Path dir) throws Exception {
        Files.createDirectories(dir);
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.init();
        return service;
    }

    /** with the LLM replaced: commit "first" gets BASE, every other one a feature named after it */
    private static ImportGitView startImport(EccoService service, Path clone, int reviewInterval) throws Exception {
        List<GitCommitInfo> oldestFirst = new ArrayList<>(new GitHistoryReader().listCommits(clone));
        Collections.reverse(oldestFirst);
        return onFx(() -> {
            ImportGitView view = new ImportGitView(service);
            new Scene(view);
            view.suggester = commit -> new ImportGitView.Suggested(new LlmFeatureSuggestionClient.SuggestionBatch(
                    List.of(commit.getMessage().equals("first") ? "BASE" : commit.getMessage().toUpperCase()), null,
                    List.of(new LlmFeatureSuggestionClient.Usage(100, 5, 42))), "stub", 7);
            view.startImport(clone, oldestFirst, true, reviewInterval);
            return view;
        });
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
