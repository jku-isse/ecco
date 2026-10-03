package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A commit that failed to import ended the whole Git import run: the error was shown and the
 * remaining commits could only be imported by starting over. Now the commit's review screen comes
 * back with the error and the configuration that was tried, so it can be corrected and imported
 * again, or skipped.
 */
public class ImportGitViewRetryTest {

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

    @Test
    @Timeout(60)
    public void failedReviewedCommitCanBeCorrectedAndImportedAgain(@TempDir Path tmp) throws Exception {
        Path clone = gitHistory(tmp.resolve("clone"));
        EccoService service = repository(tmp.resolve("repo"));
        ImportGitView view = startImport(service, clone, 1);

        waitFor(view, "Review Commit (1 of 2)");
        // a blank configuration falls back to the tree's .config, and there is none: refused
        clickButton(view, "Import");
        waitFor(view, "Review Commit (1 of 2)", true);
        assertTrue(onFx(() -> failureText(view)).contains("A commit needs at least one feature"), onFx(() -> failureText(view)));
        assertEquals(0, service.getCommits().size(), "nothing of the failed commit is kept");

        onFx(() -> {
            findField(view).setText("BASE");
            return null;
        });
        clickButton(view, "Import");
        waitFor(view, "Review Commit (2 of 2)");
        assertNull(onFx(() -> failureText(view)), "a new commit starts without the old error");
        assertEquals(1, service.getCommits().size());

        clickButton(view, "Import");
        waitFor(view, FINISHED);
        assertEquals(2, service.getCommits().size());
        service.close();
    }

    @Test
    @Timeout(60)
    public void failedAutoImportStopsForReviewInsteadOfEndingTheRun(@TempDir Path tmp) throws Exception {
        Path clone = gitHistory(tmp.resolve("clone"));
        EccoService service = repository(tmp.resolve("repo"));
        // reviews every 2nd commit: the first one is imported unattended
        ImportGitView view = startImport(service, clone, 2);

        waitFor(view, "Review Commit (1 of 2)", true);
        assertEquals(0, service.getCommits().size());

        clickButton(view, "Skip");
        waitFor(view, "Review Commit (2 of 2)");
        onFx(() -> {
            findField(view).setText("BASE");
            return null;
        });
        clickButton(view, "Import");
        waitFor(view, FINISHED);
        assertEquals(1, service.getCommits().size());
        service.close();
    }

    @Test
    public void describeFailureListsEachMessageOnce() {
        Exception inner = new IllegalStateException("A commit needs at least one feature.");
        assertEquals("Error during commit. - caused by: A commit needs at least one feature.",
                ImportGitView.describeFailure(new RuntimeException("Error during commit.", new RuntimeException("Error during commit.", inner))));
    }

    /** two commits, neither with a .config */
    private static Path gitHistory(Path dir) throws Exception {
        Files.createDirectories(dir);
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            Files.writeString(dir.resolve("a.txt"), "a\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("first").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
            Files.writeString(dir.resolve("b.txt"), "b\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("second").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
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

    private static ImportGitView startImport(EccoService service, Path clone, int reviewInterval) throws Exception {
        List<GitCommitInfo> oldestFirst = new ArrayList<>(new GitHistoryReader().listCommits(clone));
        Collections.reverse(oldestFirst);
        return onFx(() -> {
            ImportGitView view = new ImportGitView(service);
            new Scene(view);
            view.startImport(clone, oldestFirst, false, reviewInterval);
            return view;
        });
    }

    private static void waitFor(ImportGitView view, String header) throws Exception {
        waitFor(view, header, false);
    }

    /** the header a finished run shows (showSuccessHeader clears it) */
    private static final String FINISHED = "";

    /** waits until the header shows {@code header} and, if asked, an import failure */
    private static void waitFor(ImportGitView view, String header, boolean withFailure) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (onFx(() -> view.headerLabel.getText().equals(header) && (!withFailure || failureText(view) != null)))
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

    private static String failureText(ImportGitView view) {
        Node label = find(view, n -> ImportGitView.IMPORT_FAILURE_LABEL_ID.equals(n.getId()));
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
}
