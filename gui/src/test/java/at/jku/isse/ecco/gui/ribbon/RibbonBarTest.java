package at.jku.isse.ecco.gui.ribbon;

import atlantafx.base.controls.Tab;
import atlantafx.base.controls.TabLine;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kordamp.ikonli.feather.Feather;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The ribbon: one tab per spec, the first tab's band of buttons shown right away, selecting a tab
 * swaps in its band, groups are separated, and each button runs its action.
 */
public class RibbonBarTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    private final List<String> ran = new CopyOnWriteArrayList<>();

    private RibbonAction action(String label) {
        return new RibbonAction(label, Feather.CIRCLE, () -> ran.add(label), false);
    }

    private static TabLine tabLine(RibbonBar bar) {
        return (TabLine) bar.getChildren().get(0);
    }

    private static List<String> band(RibbonBar bar) {
        StackPane area = (StackPane) bar.getChildren().get(1);
        assertEquals(1, area.getChildren().size(), "exactly one band is shown");
        return ((HBox) area.getChildren().get(0)).getChildren().stream()
                .map(n -> n instanceof Button b ? b.getText() : n instanceof Separator ? "|" : n.toString()).toList();
    }

    /** The first tab's band is shown at once (no click needed), with a separator between groups only. */
    @Test
    @Timeout(30)
    public void showsTheFirstTabsBandRightAway() throws Exception {
        RibbonAction a = action("a"), b = action("b"), c = action("c"), d = action("d");
        onFx(() -> {
            RibbonBar bar = new RibbonBar(List.of(
                    new RibbonTabSpec("One", Feather.FOLDER, List.of(new RibbonGroup(List.of(a, b)), new RibbonGroup(List.of(c)))),
                    new RibbonTabSpec("Two", Feather.EYE, List.of(new RibbonGroup(List.of(d))))));
            assertEquals(List.of("One", "Two"), tabLine(bar).getTabs().stream().map(Tab::getText).toList());
            assertEquals(Tab.ClosingPolicy.NO_TABS, tabLine(bar).getTabClosingPolicy());
            assertEquals(List.of("a", "b", "|", "c"), band(bar));
        });
    }

    /** Selecting another tab swaps in its band; selecting the first again brings the first band back. */
    @Test
    @Timeout(30)
    public void selectingATabSwapsInItsBand() throws Exception {
        RibbonAction a = action("a"), d = action("d");
        onFx(() -> {
            RibbonBar bar = new RibbonBar(List.of(
                    new RibbonTabSpec("One", Feather.FOLDER, List.of(new RibbonGroup(List.of(a)))),
                    new RibbonTabSpec("Two", Feather.EYE, List.of(new RibbonGroup(List.of(d))))));
            Node firstBand = ((StackPane) bar.getChildren().get(1)).getChildren().get(0);
            tabLine(bar).getSelectionModel().select(1);
            assertEquals(List.of("d"), band(bar));
            tabLine(bar).getSelectionModel().select(0);
            assertEquals(List.of("a"), band(bar));
            assertSame(firstBand, ((StackPane) bar.getChildren().get(1)).getChildren().get(0), "bands are built once and reused");
        });
    }

    /** Each button runs its own action, and getButton hands back the button built for an action. */
    @Test
    @Timeout(30)
    public void eachButtonRunsItsAction() throws Exception {
        RibbonAction a = action("a"), b = action("b"), d = action("d");
        onFx(() -> {
            RibbonBar bar = new RibbonBar(List.of(
                    new RibbonTabSpec("One", Feather.FOLDER, List.of(new RibbonGroup(List.of(a, b)))),
                    new RibbonTabSpec("Two", Feather.EYE, List.of(new RibbonGroup(List.of(d))))));
            bar.getButton(b).fire();
            bar.getButton(d).fire();
            bar.getButton(a).fire();
            assertEquals("b", bar.getButton(b).getText());
            assertNull(bar.getButton(action("a")), "looked up by identity, not by label");
        });
        assertEquals(List.of("b", "d", "a"), ran);
    }

    /** Without any tabs the ribbon is just empty. */
    @Test
    @Timeout(30)
    public void noTabsNoBand() throws Exception {
        onFx(() -> {
            RibbonBar bar = new RibbonBar(List.of());
            assertTrue(tabLine(bar).getTabs().isEmpty());
            assertTrue(((StackPane) bar.getChildren().get(1)).getChildren().isEmpty());
        });
    }
}
