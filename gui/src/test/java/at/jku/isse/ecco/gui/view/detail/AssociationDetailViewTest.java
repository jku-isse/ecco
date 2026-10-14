package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static at.jku.isse.ecco.gui.view.detail.ArtifactDetailViewTest.association;
import static at.jku.isse.ecco.gui.view.detail.ArtifactDetailViewTest.twoFiles;
import static org.junit.jupiter.api.Assertions.*;

/** What the association detail view shows: id, presence condition and artifact snippets, or nothing. */
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
            assertTrue(onFx(() -> view.getTop().isDisable()));

            onFx(() -> view.showAssociation(a));

            assertNotNull(onFx(() -> view.getCenter()));
            assertFalse(onFx(() -> view.getTop().isDisable()));
            TextField id = onFx(() -> (TextField) find(view, n -> n instanceof TextField));
            TextArea condition = onFx(() -> (TextArea) find(view, n -> n instanceof TextArea));
            TreeView<?> snippets = onFx(() -> (TreeView<?>) find(view, n -> n instanceof TreeView));
            assertEquals(a.getId(), onFx(() -> id.getText()));
            assertEquals(a.computeCondition().toString(), onFx(() -> condition.getText()));
            assertNotNull(onFx(() -> snippets.getRoot()), "the artifact snippets");

            onFx(() -> view.showAssociation(null));
            assertNull(onFx(() -> view.getCenter()));
            assertTrue(onFx(() -> view.getTop().isDisable()));
            assertEquals("", onFx(() -> id.getText()));
            assertEquals("", onFx(() -> condition.getText()));
            assertNull(onFx(() -> snippets.getRoot()));
        }
    }
}
