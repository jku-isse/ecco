package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.RecentRepositories;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static at.jku.isse.ecco.gui.view.operation.CommitViewTest.show;
import static at.jku.isse.ecco.gui.view.operation.InitViewTest.errorLabel;
import static at.jku.isse.ecco.gui.view.operation.InitViewTest.recentPrefs;
import static org.junit.jupiter.api.Assertions.*;

/**
 * "Open...": opens the repository in the directory typed, chosen or picked from the recent ones,
 * with its parent as the working directory. What is not a repository is reported in the view, and
 * only an opened repository becomes the most recent one.
 */
public class OpenViewTest {

    private static final String RECENT_REPOS_KEY = "recentRepositoryDirs";
    private String originalRecent;

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    // opening a repository adds it to the user's recent repositories
    @BeforeEach
    public void saveRecentRepositories() {
        this.originalRecent = recentPrefs().get(RECENT_REPOS_KEY, null);
    }

    @AfterEach
    public void restoreRecentRepositories() throws Exception {
        if (this.originalRecent == null)
            recentPrefs().remove(RECENT_REPOS_KEY);
        else
            recentPrefs().put(RECENT_REPOS_KEY, this.originalRecent);
        recentPrefs().flush();
    }

    @Test
    @Timeout(60)
    public void startsWithTheServicesRepositoryDirectoryAndOffersTheRecentOnes(@TempDir Path tmp) throws Exception {
        Path recent = Files.createDirectories(tmp.resolve("recent").resolve(".ecco"));
        recentPrefs().put(RECENT_REPOS_KEY, recent + "\n" + tmp.resolve("gone").resolve(".ecco") + "\n");
        EccoService service = new EccoService();
        service.setRepositoryDir(tmp.resolve("work").resolve(".ecco"));
        CommitViewTest.Shown<OpenView> shown = show(() -> new OpenView(service));
        try {
            OpenView view = shown.view();
            assertEquals(tmp.resolve("work").resolve(".ecco").toString(), onFx(() -> field(view).getText()));
            ComboBox<?> recentBox = onFx(() -> (ComboBox<?>) find(view, n -> n instanceof ComboBox));
            assertNotNull(recentBox);
            assertEquals(List.of(recent), onFx(() -> List.copyOf(recentBox.getItems())), "only the recent ones that still exist");

            onFx(() -> ((ComboBox<Path>) recentBox).setValue(recent));
            assertEquals(recent.toString(), onFx(() -> field(view).getText()), "picking a recent one fills it in");
        } finally {
            shown.close();
        }
    }

    @Test
    @Timeout(60)
    public void noRecentRepositoriesNoChoice(@TempDir Path tmp) throws Exception {
        recentPrefs().remove(RECENT_REPOS_KEY);
        EccoService service = new EccoService();
        service.setRepositoryDir(tmp.resolve("work").resolve(".ecco"));
        CommitViewTest.Shown<OpenView> shown = show(() -> new OpenView(service));
        try {
            assertNull(onFx(() -> find(shown.view(), n -> n instanceof ComboBox)));
        } finally {
            shown.close();
        }
    }

    @Test
    @Timeout(60)
    public void opensAnExistingRepositoryWithItsParentAsWorkingDirectory(@TempDir Path tmp) throws Exception {
        Path work = tmp.resolve("work");
        try (EccoService existing = repository(work)) {
            commit(existing, work, "BASE", Map.of("f.txt", "f\n"));
        }
        try (EccoService service = new EccoService()) {
            CommitViewTest.Shown<OpenView> shown = show(() -> new OpenView(service));
            try {
                open(shown.view(), work.resolve(".ecco").toString());
                waitUntil("the dialog to close", () -> !shown.stage().isShowing());

                assertTrue(service.isInitialized());
                assertEquals(work.resolve(".ecco"), service.getRepositoryDir());
                assertEquals(work, service.getBaseDir());
                assertEquals(1, service.getCommits().size());
                assertEquals(work.resolve(".ecco"), RecentRepositories.getRecentRepositories().get(0));
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(60)
    public void aMissingRepositoryIsReported(@TempDir Path tmp) throws Exception {
        Path missing = tmp.resolve("missing").resolve(".ecco");
        String error = failedOpen(missing.toString(), "Error opening repository.");
        assertTrue(error.contains("Repository does not exist."), error);
    }

    @Test
    @Timeout(60)
    public void aWorkingDirectoryIsNoRepository(@TempDir Path tmp) throws Exception {
        Path work = tmp.resolve("work");
        repository(work).close();
        String error = failedOpen(work.toString(), "Error opening repository.");
        assertTrue(error.contains("Not an ECCO repository: " + work), error);
        assertTrue(error.contains("did you mean " + work.resolve(".ecco")), error);
    }

    @Test
    @Timeout(60)
    public void aRelativeDirectoryIsRefused() throws Exception {
        // an empty field set the repository directory to "" and then failed on its missing parent
        // in the click handler: nothing was shown, and the service kept the "" directory
        try (EccoService service = new EccoService()) {
            Path before = service.getRepositoryDir();
            CommitViewTest.Shown<OpenView> shown = show(() -> new OpenView(service));
            try {
                OpenView view = shown.view();
                open(view, "");
                waitUntil("the error", () -> view.toolBar.getStyleClass().contains("error"));

                assertTrue(onFx(() -> errorLabel(view)).startsWith("Not a valid repository directory"), onFx(() -> errorLabel(view)));
                assertFalse(service.isInitialized());
                assertEquals(before, service.getRepositoryDir(), "the service is left as it was");
            } finally {
                shown.close();
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /** opens {@code text}, expects the error {@code message}, and returns the exception shown */
    private String failedOpen(String text, String message) throws Exception {
        try (EccoService service = new EccoService()) {
            CommitViewTest.Shown<OpenView> shown = show(() -> new OpenView(service));
            try {
                OpenView view = shown.view();
                open(view, text);
                waitUntil("the error", () -> view.toolBar.getStyleClass().contains("error"));

                assertFalse(service.isInitialized());
                assertEquals(message, onFx(() -> errorLabel(view)));
                assertTrue(shown.stage().isShowing());
                assertFalse(RecentRepositories.getRecentRepositories().contains(Path.of(text)), "not a recent repository");
                return onFx(() -> ((ExceptionTextArea) find(view, n -> n instanceof ExceptionTextArea)).getText());
            } finally {
                shown.close();
            }
        }
    }

    private static void open(OpenView view, String repositoryDir) throws Exception {
        onFx(() -> {
            field(view).setText(repositoryDir);
            button(view, "Open").fire();
        });
    }

    private static TextField field(Node view) {
        return (TextField) find(view, n -> n instanceof TextField);
    }
}
