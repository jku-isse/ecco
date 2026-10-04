package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static at.jku.isse.ecco.gui.view.operation.CommitViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * "Commit": commits the service's base directory with the message and configuration entered, the
 * configuration defaulting to the feature names of the latest commit and a blank one falling back to
 * the folder's .config. A failed commit is reported in the view and commits nothing.
 */
public class CommitBaseDirViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    @Timeout(60)
    public void showsTheBaseDirectoryAndDefaultsToTheFeaturesOfTheLatestCommit(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "BASE, A", Map.of("f.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "BASE, B", Map.of("f.txt", "b\n"));
            Path work = Files.createDirectories(tmp.resolve("work"));
            service.setBaseDir(work);
            CommitViewTest.Shown<CommitBaseDirView> shown = show(() -> new CommitBaseDirView(service));
            try {
                List<TextField> fields = onFx(() -> fields(shown.view()));
                assertEquals(work.toString(), fields.get(0).getText());
                assertFalse(fields.get(0).isEditable(), "the folder is always the base directory");
                assertEquals("", fields.get(1).getText(), "no default message");
                // bare feature names, which stand for their latest revisions
                assertEquals(Set.of("BASE", "B"), Set.of(fields.get(2).getText().split(", ")));
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(60)
    public void aNewRepositoryHasNoDefaultConfiguration(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.setBaseDir(Files.createDirectories(tmp.resolve("work")));
            CommitViewTest.Shown<CommitBaseDirView> shown = show(() -> new CommitBaseDirView(service));
            try {
                assertEquals("", onFx(() -> fields(shown.view()).get(2).getText()));
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(60)
    public void commitsTheBaseDirectoryWithTheEnteredMessageAndConfiguration(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            Path work = CommitViewTest.variant(tmp, "work", "BASE, C", "base\n");
            service.setBaseDir(work);
            CommitViewTest.Shown<CommitBaseDirView> shown = show(() -> new CommitBaseDirView(service));
            try {
                CommitBaseDirView view = shown.view();
                onFx(() -> {
                    fields(view).get(1).setText("first commit");
                    fields(view).get(2).setText("BASE, A");
                    button(view, "Commit").fire();
                });
                waitUntil("the commit", () -> hasButton(view, "Done"));

                assertTrue(onFx(() -> view.toolBar.getStyleClass().contains("success")));
                Commit commit = single(service);
                assertEquals("first commit", commit.getCommitMessage());
                assertEquals(Set.of("BASE", "A"), features(commit), "the entered configuration, not the .config");
                assertTrue(onFx(() -> log(view)).contains("Read file.txt using ("), onFx(() -> log(view)));
                assertTrue(onFx(() -> splitItems(view).stream().anyMatch(n -> n instanceof CommitDetailView)));
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(60)
    public void aBlankConfigurationFallsBackToTheConfigFile(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            Path work = CommitViewTest.variant(tmp, "work", "BASE, C", "base\n");
            service.setBaseDir(work);
            CommitViewTest.Shown<CommitBaseDirView> shown = show(() -> new CommitBaseDirView(service));
            try {
                CommitBaseDirView view = shown.view();
                onFx(() -> {
                    fields(view).get(2).setText("  ");
                    button(view, "Commit").fire();
                });
                waitUntil("the commit", () -> hasButton(view, "Done"));

                assertEquals(Set.of("BASE", "C"), features(single(service)));
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(60)
    public void aFailedCommitIsReportedAndCommitsNothing(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            Path work = CommitViewTest.variant(tmp, "work", null, "base\n");
            service.setBaseDir(work);
            CommitViewTest.Shown<CommitBaseDirView> shown = show(() -> new CommitBaseDirView(service));
            try {
                CommitBaseDirView view = shown.view();
                onFx(() -> button(view, "Commit").fire());
                waitUntil("the commit", () -> hasButton(view, "Done"));

                assertTrue(onFx(() -> view.toolBar.getStyleClass().contains("error")));
                assertEquals(0, service.getCommits().size());
                String error = onFx(() -> splitItems(view).stream().filter(n -> n instanceof ExceptionTextArea)
                        .map(n -> ((ExceptionTextArea) n).getText()).findFirst().orElse(null));
                assertNotNull(error, "the failure is shown");
                assertTrue(error.contains("at least one feature"), error);
            } finally {
                shown.close();
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /** folder, commit message and configuration, in that order */
    private static List<TextField> fields(Node view) {
        return findAll(view, n -> n instanceof TextField)
                .stream().map(n -> (TextField) n).toList();
    }

    private static Commit single(EccoService service) {
        List<Commit> commits = new ArrayList<>(service.getCommits());
        assertEquals(1, commits.size());
        return commits.get(0);
    }

    private static String log(Node root) {
        return ((TextArea) splitItems(root).get(0)).getText();
    }
}
