package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.RecentRepositories;
import javafx.scene.Node;
import javafx.scene.control.Label;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static at.jku.isse.ecco.gui.view.operation.CommitViewTest.show;
import static org.junit.jupiter.api.Assertions.*;

/**
 * "New": initializes a repository in the directory typed or chosen. A missing parent directory is
 * only created, and an existing repository only deleted, when the user confirms; a failure is
 * reported in the view; a new repository becomes the most recent one.
 */
public class InitViewTest {

    private static final String RECENT_REPOS_KEY = "recentRepositoryDirs";
    private String originalRecent;

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    // a successful init adds the new repository to the user's recent repositories
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

    /** An InitView whose confirmations are answered by the test. */
    private static class AnsweredInitView extends InitView {
        final List<String> asked = new CopyOnWriteArrayList<>();
        volatile boolean answer = false;

        AnsweredInitView(EccoService service) {
            super(service);
        }

        @Override
        boolean confirm(String header, String text) {
            this.asked.add(header);
            return this.answer;
        }
    }

    @Test
    @Timeout(60)
    public void startsWithTheServicesRepositoryDirectory(@TempDir Path tmp) throws Exception {
        EccoService service = new EccoService();
        service.setRepositoryDir(tmp.resolve("work").resolve(".ecco"));
        CommitViewTest.Shown<AnsweredInitView> shown = show(() -> new AnsweredInitView(service));
        try {
            assertEquals(tmp.resolve("work").resolve(".ecco").toString(), onFx(() -> field(shown.view()).getText()));
        } finally {
            shown.close();
        }
    }

    @Test
    @Timeout(60)
    public void initializesInAnExistingDirectoryWithoutAsking(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        try (EccoService service = new EccoService()) {
            CommitViewTest.Shown<AnsweredInitView> shown = show(() -> new AnsweredInitView(service));
            try {
                init(shown, work.resolve(".ecco"));
                waitUntil("the dialog to close", () -> !shown.stage().isShowing());

                assertEquals(List.of(), shown.view().asked);
                assertTrue(service.isInitialized());
                assertEquals(work.resolve(".ecco"), service.getRepositoryDir());
                assertEquals(work, service.getBaseDir(), "the repository's working directory is its parent");
                assertEquals(work.resolve(".ecco"), RecentRepositories.getRecentRepositories().get(0));
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(60)
    public void aMissingDirectoryIsOnlyCreatedWhenConfirmed(@TempDir Path tmp) throws Exception {
        Path work = tmp.resolve("new").resolve("work");
        try (EccoService service = new EccoService()) {
            CommitViewTest.Shown<AnsweredInitView> shown = show(() -> new AnsweredInitView(service));
            try {
                AnsweredInitView view = shown.view();
                view.answer = false;
                init(shown, work.resolve(".ecco"));
                assertEquals(List.of("Create Directory"), view.asked);
                assertFalse(Files.exists(tmp.resolve("new")), "declined: nothing is created");
                assertFalse(service.isInitialized());
                assertEquals("Repository Directory", onFx(() -> view.headerLabel.getText()), "declined: the dialog stays as it was");

                view.answer = true;
                init(shown, work.resolve(".ecco"));
                waitUntil("the dialog to close", () -> !shown.stage().isShowing());
                assertTrue(Files.isDirectory(work.resolve(".ecco")));
                assertTrue(service.isInitialized());
                assertEquals(work, service.getBaseDir());
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(60)
    public void anExistingRepositoryIsOnlyReplacedWhenConfirmed(@TempDir Path tmp) throws Exception {
        Path work = tmp.resolve("work");
        try (EccoService existing = repository(work)) {
            commit(existing, work, "BASE", Map.of("f.txt", "f\n"));
        }
        try (EccoService service = new EccoService()) {
            CommitViewTest.Shown<AnsweredInitView> shown = show(() -> new AnsweredInitView(service));
            try {
                AnsweredInitView view = shown.view();
                view.answer = false;
                init(shown, work.resolve(".ecco"));
                assertEquals(List.of("Delete Existing Repository"), view.asked);
                assertFalse(service.isInitialized());
                assertEquals(1, commitsIn(work), "declined: the repository is kept");

                view.answer = true;
                init(shown, work.resolve(".ecco"));
                waitUntil("the dialog to close", () -> !shown.stage().isShowing());
                assertTrue(service.isInitialized());
                assertEquals(0, service.getCommits().size(), "confirmed: a fresh repository");
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(60)
    public void aFailedInitIsReported(@TempDir Path tmp) throws Exception {
        // the "directory" for the repository is a file
        Path file = Files.writeString(tmp.resolve("file.txt"), "not a directory\n");
        try (EccoService service = new EccoService()) {
            CommitViewTest.Shown<AnsweredInitView> shown = show(() -> new AnsweredInitView(service));
            try {
                AnsweredInitView view = shown.view();
                init(shown, file.resolve(".ecco"));
                waitUntil("the error", () -> view.toolBar.getStyleClass().contains("error"));

                assertFalse(service.isInitialized());
                assertEquals("Error initializing repository.", onFx(() -> errorLabel(view)));
                assertNotNull(onFx(() -> find(view, n -> n instanceof ExceptionTextArea)), "the exception is shown");
                assertTrue(shown.stage().isShowing());
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(60)
    public void anExistingDirectoryThatIsNoRepositoryIsNeverOfferedForDeletion(@TempDir Path tmp) throws Exception {
        // e.g. a working directory typed in instead of its .ecco: it was offered for deletion as "a
        // repository", and confirming deleted it with everything in it
        Path project = Files.createDirectories(tmp.resolve("project"));
        Files.writeString(project.resolve("main.c"), "int main() { return 0; }\n");
        try (EccoService service = new EccoService()) {
            CommitViewTest.Shown<AnsweredInitView> shown = show(() -> new AnsweredInitView(service));
            try {
                AnsweredInitView view = shown.view();
                view.answer = true;
                init(shown, project);
                waitUntil("the error", () -> view.toolBar.getStyleClass().contains("error") || !shown.stage().isShowing());

                assertEquals(List.of(), view.asked);
                assertTrue(Files.exists(project.resolve("main.c")), "the directory is kept");
                assertFalse(service.isInitialized());
                assertTrue(onFx(() -> errorLabel(view)).contains(project.toString()), onFx(() -> errorLabel(view)));
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(60)
    public void aRelativeDirectoryIsRefused(@TempDir Path tmp) throws Exception {
        // an empty field is the directory the GUI runs in, which was offered for deletion as "a
        // repository" (never confirmed here); a relative one resolves against it as well
        try (EccoService service = new EccoService()) {
            for (String text : List.of("", "work/.ecco")) {
                CommitViewTest.Shown<AnsweredInitView> shown = show(() -> new AnsweredInitView(service));
                try {
                    AnsweredInitView view = shown.view();
                    view.answer = false;
                    init(shown, text);
                    waitUntil("the error", () -> view.toolBar.getStyleClass().contains("error") || !view.asked.isEmpty());

                    assertEquals(List.of(), view.asked, "'" + text + "'");
                    assertFalse(service.isInitialized());
                    assertTrue(onFx(() -> errorLabel(view)).startsWith("Not a valid repository directory"), onFx(() -> errorLabel(view)));
                } finally {
                    shown.close();
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static void init(CommitViewTest.Shown<AnsweredInitView> shown, Path repositoryDir) throws Exception {
        init(shown, repositoryDir.toString());
    }

    private static void init(CommitViewTest.Shown<AnsweredInitView> shown, String repositoryDir) throws Exception {
        onFx(() -> {
            field(shown.view()).setText(repositoryDir);
            button(shown.view(), "Init").fire();
        });
    }

    private static TextField field(Node view) {
        return (TextField) find(view, n -> n instanceof TextField);
    }

    /** the text of the first label in the view's content, where stepError puts its message */
    static String errorLabel(OperationView view) {
        Node label = find(view.getCenter(), n -> n instanceof Label);
        return label == null ? null : ((Label) label).getText();
    }

    private static int commitsIn(Path work) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.open();
            return service.getCommits().size();
        }
    }

    static Preferences recentPrefs() {
        return Preferences.userNodeForPackage(RecentRepositories.class);
    }
}
