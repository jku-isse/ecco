package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.gui.view.detail.RemoteDetailView;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Remotes tab decides: which remotes it lists, what Add and Remove do to the repository's
 * remotes (and which type Add infers from the address), that Refresh picks up remotes added
 * elsewhere, and which remote the detail pane shows. Add's failure path opens a modal alert and is
 * not driven here.
 */
public class RemotesViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    @Timeout(60)
    public void listsTheRepositorysRemotes(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.addRemote("origin", tmp.resolve("o").toString(), Remote.Type.LOCAL);
            service.addRemote("server", "localhost:3770", Remote.Type.REMOTE);
            RemotesView view = onFx(() -> new RemotesView(service));
            waitUntil("both remotes", () -> table(view).getItems().size() == 2);

            assertEquals(Set.of("origin", "server"), onFx(() -> names(view)));
            assertFalse(onFx(view::isDisable));
        }
    }

    @Test
    @Timeout(60)
    public void anUninitializedServiceLeavesTheViewDisabled(@TempDir Path tmp) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            RemotesView view = onFx(() -> new RemotesView(service));
            waitUntil("disabled", view::isDisable);
            assertTrue(onFx(() -> table(view).getItems().isEmpty()));
        }
    }

    /** A host:port address makes a REMOTE remote, anything else a LOCAL one (a directory). */
    @Test
    @Timeout(60)
    public void addCreatesTheRemoteWithTheTypeItsAddressImplies(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            RemotesView view = loaded(service);
            String dir = tmp.resolve("elsewhere").toString();
            onFx(() -> {
                fields(view).get(0).setText("local");
                fields(view).get(1).setText(dir);
                button(view, "Add").fire();
                fields(view).get(0).setText("server");
                fields(view).get(1).setText("example.org:3770");
                button(view, "Add").fire();
            });
            waitUntil("both remotes listed", () -> names(view).equals(Set.of("local", "server")));

            assertEquals(Remote.Type.LOCAL, service.getRemote("local").getType());
            assertEquals(dir, service.getRemote("local").getAddress());
            assertEquals(Remote.Type.REMOTE, service.getRemote("server").getType());
            assertEquals("example.org:3770", service.getRemote("server").getAddress());
        }
    }

    @Test
    @Timeout(60)
    public void removeTakesTheSelectedRemoteOutOfTheRepository(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.addRemote("origin", tmp.resolve("o").toString(), Remote.Type.LOCAL);
            service.addRemote("other", tmp.resolve("p").toString(), Remote.Type.LOCAL);
            RemotesView view = onFx(() -> new RemotesView(service));
            waitUntil("both remotes", () -> table(view).getItems().size() == 2);

            onFx(() -> {
                select(view, "other");
                button(view, "Remove").fire();
            });
            assertEquals(Set.of("origin"), onFx(() -> names(view)));
            assertNull(service.getRemote("other"));
            assertNotNull(service.getRemote("origin"));

            onFx(() -> {
                table(view).getSelectionModel().clearSelection();
                button(view, "Remove").fire();
            });
            assertNotNull(service.getRemote("origin"), "nothing selected, nothing removed");
        }
    }

    @Test
    @Timeout(60)
    public void refreshListsRemotesAddedElsewhere(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            RemotesView view = loaded(service);
            service.addRemote("late", tmp.resolve("l").toString(), Remote.Type.LOCAL);
            assertEquals(Set.of(), onFx(() -> names(view)), "adding a remote fires no event the view listens to");

            onFx(() -> button(view, "Refresh").fire());
            waitUntil("the new remote and the toolbar enabled again",
                    () -> names(view).equals(Set.of("late")) && !toolBar(view).isDisable());
        }
    }

    @Test
    @Timeout(60)
    public void selectingARemoteShowsItInTheDetailPane(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.addRemote("origin", "localhost:3770", Remote.Type.REMOTE);
            RemotesView view = onFx(() -> new RemotesView(service));
            waitUntil("the remote", () -> table(view).getItems().size() == 1);
            RemoteDetailView detail = onFx(() -> (RemoteDetailView) find(view, n -> n instanceof RemoteDetailView));
            assertNull(onFx(detail::getCenter), "nothing selected: nothing shown");

            onFx(() -> select(view, "origin"));
            assertNotNull(onFx(detail::getCenter));
            assertEquals(List.of("origin", "localhost:3770"),
                    onFx(() -> findAll(detail, n -> n instanceof TextField).stream().map(n -> ((TextField) n).getText()).toList()));

            onFx(() -> table(view).getSelectionModel().clearSelection());
            assertNull(onFx(detail::getCenter), "deselected: nothing shown");
        }
    }

    /** A Save in the detail pane updates the list and keeps the renamed remote selected. */
    @Test
    @Timeout(60)
    public void aSaveInTheDetailPaneUpdatesTheList(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            service.addRemote("origin", "localhost:3770", Remote.Type.REMOTE);
            RemotesView view = loaded(service);
            RemoteDetailView detail = onFx(() -> (RemoteDetailView) find(view, n -> n instanceof RemoteDetailView));
            onFx(() -> select(view, "origin"));

            onFx(() -> {
                ((TextField) find(detail, n -> n instanceof TextField)).setText("upstream");
                button(detail, "Save").fire();
            });

            assertEquals(Set.of("upstream"), onFx(() -> names(view)));
            assertEquals("upstream", onFx(() -> table(view).getSelectionModel().getSelectedItem().getName()));
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /**
     * A new view whose initial listing has run: it is two runLaters deep and replaces the table's
     * rows with the remotes as they were when the view was built.
     */
    private static RemotesView loaded(EccoService service) throws Exception {
        RemotesView view = onFx(() -> new RemotesView(service));
        onFx(() -> {
        });
        onFx(() -> {
        });
        return view;
    }

    @SuppressWarnings("unchecked")
    private static TableView<Remote> table(RemotesView view) {
        return (TableView<Remote>) find(view, n -> n instanceof TableView);
    }

    private static Set<String> names(RemotesView view) {
        return table(view).getItems().stream().map(Remote::getName).collect(Collectors.toSet());
    }

    private static void select(RemotesView view, String name) {
        table(view).getSelectionModel().select(table(view).getItems().stream().filter(r -> r.getName().equals(name)).findFirst().orElseThrow());
    }

    private static ToolBar toolBar(RemotesView view) {
        return (ToolBar) view.getTop();
    }

    /** The toolbar's name and address fields. */
    private static List<TextField> fields(RemotesView view) {
        return toolBar(view).getItems().stream().filter(n -> n instanceof TextField).map(n -> (TextField) n).toList();
    }
}
