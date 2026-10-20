package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static at.jku.isse.ecco.gui.view.detail.ArtifactDetailViewTest.association;
import static at.jku.isse.ecco.gui.view.detail.ArtifactDetailViewTest.twoFiles;
import static org.junit.jupiter.api.Assertions.*;

/** What the association detail view shows: id, presence condition with its modules, and artifact snippets, or nothing. */
public class AssociationDetailViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    /** An association shows its id, its condition and its artifacts; null empties and disables the view. */
    @Test
    @Timeout(60)
    public void showsTheAssociationsIdConditionAndArtifacts(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Association a = association(service, "a.txt");
            AssociationDetailView view = onFx(() -> new AssociationDetailView(service));
            SplitPane split = onFx(() -> (SplitPane) find(view, n -> n instanceof SplitPane));
            assertNull(split, "nothing shown initially");
            assertNull(onFx(() -> view.getTop()), "no toolbar: it held nothing");

            onFx(() -> view.showAssociation(a));
            waitUntil("the association is read", () -> !view.isReading());

            assertNotNull(onFx(() -> view.getCenter()));
            TextField id = onFx(() -> (TextField) find(view, n -> n instanceof TextField));
            TextArea condition = onFx(() -> (TextArea) find(view, n -> n instanceof TextArea));
            TreeView<?> snippets = onFx(() -> (TreeView<?>) find(view, n -> n instanceof TreeView));
            assertEquals(a.getId(), onFx(() -> id.getText()));
            assertEquals(a.computeCondition().toString(), onFx(() -> condition.getText()));
            assertNotNull(onFx(() -> snippets.getRoot()), "the artifact snippets");

            // the Modules tab: the whole module lattice, with the d^N notation, counts and which are absorbed
            TableView<?> modules = onFx(() -> (TableView<?>) find(view, n -> n instanceof TableView));
            List<AssociationDetailView.ModuleRow> rows = AssociationDetailView.moduleRows(a);
            int lattice = a.computeCondition().getModules().values().stream().mapToInt(java.util.Collection::size).sum();
            assertEquals(lattice, rows.size());
            assertEquals(rows.size(), onFx(() -> modules.getItems().size()));
            assertEquals(a.computeCondition().getAbsorbedModuleRevisions().size(), rows.stream().filter(AssociationDetailView.ModuleRow::inCondition).count(),
                    "the terms of the condition shown");
            for (AssociationDetailView.ModuleRow row : rows) {
                assertTrue(row.moduleRevision().startsWith("d^" + row.order() + "("), row.moduleRevision());
                assertTrue(row.count() > 0, "observed: " + row);
            }
            assertEquals(List.of("Order", "Module revision", "Count", "In condition"),
                    onFx(() -> modules.getColumns().stream().map(c -> c.getText()).toList()));

            onFx(() -> view.showAssociation(null));
            assertNull(onFx(() -> view.getCenter()));
            assertEquals("", onFx(() -> id.getText()));
            assertEquals("", onFx(() -> condition.getText()));
            assertNull(onFx(() -> snippets.getRoot()));
            assertEquals(0, onFx(() -> modules.getItems().size()));
        }
    }

    /**
     * The condition, modules and artifacts are read in the background, holding the service's lock:
     * while a commit holds it, the FX thread goes on (it computed the condition and read the counters
     * itself, while the commit changed them), the id shown at once; released, the rest is shown.
     */
    @Test
    @Timeout(60)
    public void theAssociationIsReadOffTheFxThreadHoldingTheLock(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Association a = association(service, "a.txt");
            AssociationDetailView view = onFx(() -> new AssociationDetailView(service));
            java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            Thread commitLike = new Thread(() -> {
                synchronized (service) {
                    locked.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            commitLike.start();
            try {
                assertTrue(locked.await(10, java.util.concurrent.TimeUnit.SECONDS));
                // onFx fails if the FX thread does not get to it within 10 s
                onFx(() -> view.showAssociation(a));
                Thread.sleep(300);
                assertTrue(onFx(view::isReading), "not read while the lock is held");
                TextField id = onFx(() -> (TextField) find(view, n -> n instanceof TextField));
                TextArea condition = onFx(() -> (TextArea) find(view, n -> n instanceof TextArea));
                assertEquals(a.getId(), onFx(() -> id.getText()));
                assertEquals("Reading the condition...", onFx(() -> condition.getText()));

                release.countDown();
                waitUntil("read once the lock is released", () -> !view.isReading());
                assertEquals(a.computeCondition().toString(), onFx(() -> condition.getText()));
                TreeView<?> snippets = onFx(() -> (TreeView<?>) find(view, n -> n instanceof TreeView));
                assertNotNull(onFx(() -> snippets.getRoot()), "the artifact snippets");
            } finally {
                release.countDown();
                commitLike.join(10_000);
            }
        }
    }
}
