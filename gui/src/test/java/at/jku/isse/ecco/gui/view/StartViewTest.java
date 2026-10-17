package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.RecentRepositories;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What ECCO shows while no repository is open: the recent repositories to open with a double-click,
 * New and Open for others - the window was empty.
 */
public class StartViewTest {

    private static final String RECENT_REPOS_KEY = "recentRepositoryDirs";
    private String originalRecent;

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

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

    private static Preferences recentPrefs() {
        return Preferences.userNodeForPackage(RecentRepositories.class);
    }

    @Test
    @Timeout(60)
    public void theRecentRepositoriesAreListedAndOneOpensWithADoubleClick(@TempDir Path tmp) throws Exception {
        Path work = tmp.resolve("work");
        repository(work).close();
        recentPrefs().put(RECENT_REPOS_KEY, work.resolve(".ecco") + "\n" + tmp.resolve("gone").resolve(".ecco") + "\n");
        try (EccoService service = new EccoService()) {
            StartView view = onFx(() -> new StartView(service, () -> { }, () -> { }, () -> { }));
            assertEquals(List.of(work.resolve(".ecco")), onFx(() -> List.copyOf(list(view).getItems())), "only those that still exist");

            onFx(() -> {
                list(view).getSelectionModel().select(0);
                view.openSelected();
            });
            waitUntil("opened", service::isInitialized);
            assertEquals(work.resolve(".ecco"), service.getRepositoryDir());
            assertEquals(work, service.getBaseDir(), "its working folder");
            assertEquals(work.resolve(".ecco"), RecentRepositories.getRecentRepositories().get(0));
        }
    }

    @Test
    @Timeout(60)
    public void withNoRecentOnesItSaysSoAndOffersNewAndOpen() throws Exception {
        recentPrefs().remove(RECENT_REPOS_KEY);
        AtomicInteger news = new AtomicInteger(), opens = new AtomicInteger();
        try (EccoService service = new EccoService()) {
            StartView view = onFx(() -> new StartView(service, news::incrementAndGet, opens::incrementAndGet, () -> { }));
            assertTrue(onFx(() -> list(view).getItems().isEmpty()));
            assertTrue(onFx(() -> ((Label) list(view).getPlaceholder()).getText()).startsWith("No repository opened yet"));
            onFx(() -> {
                button(view, "New...").fire();
                button(view, "Open Other...").fire();
            });
            assertEquals(1, news.get());
            assertEquals(1, opens.get());
            assertTrue(onFx(() -> button(view, "Open").isDisable()), "nothing selected");
        }
    }

    @Test
    @Timeout(60)
    public void aRepositoryThatCannotBeOpenedSaysWhy(@TempDir Path tmp) throws Exception {
        // a folder named .ecco that is no repository
        Path broken = Files.createDirectories(tmp.resolve("broken").resolve(".ecco"));
        recentPrefs().put(RECENT_REPOS_KEY, broken + "\n");
        try (EccoService service = new EccoService()) {
            StartView view = onFx(() -> new StartView(service, () -> { }, () -> { }, () -> { }));
            onFx(() -> {
                list(view).getSelectionModel().select(0);
                view.openSelected();
            });
            waitUntil("the error", () -> view.error() != null);
            assertTrue(onFx(view::error).startsWith("Could not open " + tmp.resolve("broken") + ": "), onFx(view::error));
            assertFalse(service.isInitialized());
        }
    }

    /** Remove from List takes it off the list, and leaves the repository as it is. */
    @Test
    @Timeout(60)
    public void removeFromListKeepsTheRepository(@TempDir Path tmp) throws Exception {
        Path a = tmp.resolve("a"), b = tmp.resolve("b");
        repository(a).close();
        repository(b).close();
        recentPrefs().put(RECENT_REPOS_KEY, a.resolve(".ecco") + "\n" + b.resolve(".ecco") + "\n");
        try (EccoService service = new EccoService()) {
            StartView view = onFx(() -> new StartView(service, () -> { }, () -> { }, () -> { }));
            onFx(() -> {
                list(view).getSelectionModel().select(a.resolve(".ecco"));
                button(view, "Remove from List").fire();
            });
            assertEquals(List.of(b.resolve(".ecco")), onFx(() -> List.copyOf(list(view).getItems())));
            assertEquals(List.of(b.resolve(".ecco")), RecentRepositories.getRecentRepositories(), "remembered so");
            assertTrue(Files.isDirectory(a.resolve(".ecco")), "the repository is kept");
        }
    }

    /**
     * Delete Repository asks first; it deletes the repository - the .ecco folder - and keeps the files
     * worked on; an open one is closed first.
     */
    @Test
    @Timeout(60)
    public void deleteRepositoryAsksAndDeletesOnlyTheRepository(@TempDir Path tmp) throws Exception {
        Path work = tmp.resolve("work");
        repository(work).close();
        Files.writeString(work.resolve("mine.txt"), "mine\n");
        recentPrefs().put(RECENT_REPOS_KEY, work.resolve(".ecco") + "\n");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.setBaseDir(work);
            service.open();
            List<Path> asked = new java.util.ArrayList<>();
            boolean[] answer = {false};
            StartView view = onFx(() -> new StartView(service, () -> { }, () -> { }, () -> { }) {
                @Override
                boolean confirmDelete(Path repositoryDir) {
                    asked.add(repositoryDir);
                    return answer[0];
                }
            });
            onFx(() -> {
                list(view).getSelectionModel().select(0);
                button(view, "Delete Repository...").fire();
            });
            assertEquals(List.of(work.resolve(".ecco")), asked);
            assertTrue(Files.isDirectory(work.resolve(".ecco")), "declined: nothing deleted");
            assertTrue(service.isInitialized());

            answer[0] = true;
            onFx(() -> button(view, "Delete Repository...").fire());
            waitUntil("deleted", () -> !Files.exists(work.resolve(".ecco")));
            assertFalse(service.isInitialized(), "closed first");
            assertTrue(Files.exists(work.resolve("mine.txt")), "the files worked on are kept");
            assertEquals(List.of(), RecentRepositories.getRecentRepositories());
            assertTrue(onFx(() -> list(view).getItems().isEmpty()));
        }
    }

    /** With a repository open, opening another one closes it first; the open one is marked in the list. */
    @Test
    @Timeout(60)
    public void openingAnotherOneSwitchesToIt(@TempDir Path tmp) throws Exception {
        Path a = tmp.resolve("a"), b = tmp.resolve("b");
        repository(a).close();
        repository(b).close();
        recentPrefs().put(RECENT_REPOS_KEY, a.resolve(".ecco") + "\n" + b.resolve(".ecco") + "\n");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(a.resolve(".ecco"));
            service.setBaseDir(a);
            service.open();
            StartView view = onFx(() -> new StartView(service, () -> { }, () -> { }, () -> { }));
            assertEquals(a.resolve(".ecco"), onFx(view::openRepository), "marked as open");

            onFx(() -> {
                list(view).getSelectionModel().select(b.resolve(".ecco"));
                view.openSelected();
            });
            waitUntil("switched", () -> service.isInitialized() && b.resolve(".ecco").equals(service.getRepositoryDir()));
        }
    }

    @SuppressWarnings("unchecked")
    private static ListView<Path> list(StartView view) {
        return (ListView<Path>) find(view, n -> n instanceof ListView);
    }
}
