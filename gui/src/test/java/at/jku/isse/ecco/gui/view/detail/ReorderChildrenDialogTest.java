package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The decisions of the Reorder dialog an ORDER warning opens: which children are locked because the
 * partial order graph already fixes their order relative to both neighbors, when Move Up/Move Down
 * are enabled (never across a fixed pair), the "Nothing to reorder" case, and what it returns. Built
 * from real repositories with ambiguous orders and driven without showing the dialog.
 */
public class ReorderChildrenDialogTest {

    private static final String ORDER_FIXED = "Order fixed by an earlier commit";

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    /** Two variants each add a line after "a": b and c are open, a is locked before both. */
    @Test
    @Timeout(60)
    public void twoVariantsLockTheSharedLineAndLetTheOthersSwap(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "BASE, A", Map.of("f.txt", "a\nb\n"));
            commit(service, tmp.resolve("v2"), "BASE, B", Map.of("f.txt", "a\nc\n"));
            Node.Op file = orderWarning(service, tmp.resolve("out"), "BASE, A, B");
            assertEquals(List.of("a", "c", "b"), texts(file.getChildren()));

            Fixture d = onFx(() -> new Fixture(new ReorderChildrenDialog(service, file)));
            assertEquals(List.of(ButtonType.OK, ButtonType.CANCEL), onFx(() -> d.dialog.getDialogPane().getButtonTypes()));
            assertNull(onFx(d::banner));
            assertEquals(List.of(true, false, false), onFx(d::locked));

            // the first row is selected: it is at the top, and "a" before "c" is fixed
            assertEquals(0, (int) onFx(d::selected));
            assertButtons(d, false, false, "Move Up", ORDER_FIXED);

            onFx(() -> d.select(1));
            assertButtons(d, false, true, ORDER_FIXED, "Move Down");
            onFx(() -> d.down.fire());
            assertEquals(List.of("a", "b", "c"), onFx(d::order));
            assertEquals(2, (int) onFx(d::selected), "the selection moves with the child");
            assertButtons(d, true, false, "Move Up", "Move Down");
            assertEquals(List.of(true, false, false), onFx(d::locked));

            onFx(() -> d.up.fire());
            assertEquals(List.of("a", "c", "b"), onFx(d::order));
            onFx(() -> d.down.fire());

            assertEquals(List.of("a", "b", "c"), texts(onFx(() -> d.dialog.getResultConverter().call(ButtonType.OK))));
            assertNull(onFx(() -> d.dialog.getResultConverter().call(ButtonType.CANCEL)));
            assertNull(onFx(() -> d.dialog.getResultConverter().call(ButtonType.CLOSE)));
            assertEquals(List.of("a", "c", "b"), texts(file.getChildren()), "the node itself is not reordered");
        }
    }

    /**
     * Three variants: a b c (A), a x (B) and a b y c (A, C). a < b < y < c and a < x are fixed, x is
     * open against b, y and c. A row is locked exactly when both its neighbors are fixed against it,
     * and no sequence of moves ever crosses a fixed pair: x can go anywhere after a, nothing else moves.
     */
    @Test
    @Timeout(60)
    public void threeVariantsLetOnlyTheOpenChildMoveAndNeverAcrossAFixedPair(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "BASE, A", Map.of("f.txt", "a\nb\nc\n"));
            commit(service, tmp.resolve("v2"), "BASE, B", Map.of("f.txt", "a\nx\n"));
            commit(service, tmp.resolve("v3"), "BASE, A, C", Map.of("f.txt", "a\nb\ny\nc\n"));
            Node.Op file = orderWarning(service, tmp.resolve("out"), "BASE, A, B, C");
            assertEquals(List.of("a", "x", "b", "y", "c"), texts(file.getChildren()));
            Set<List<String>> fixed = Set.of(
                    List.of("a", "b"), List.of("a", "y"), List.of("a", "c"), List.of("b", "y"),
                    List.of("b", "c"), List.of("y", "c"), List.of("a", "x"));

            Fixture d = onFx(() -> new Fixture(new ReorderChildrenDialog(service, file)));
            assertEquals(List.of(true, false, false, true, true), onFx(d::locked));

            // y (row 3) can go neither up past b nor down past c
            onFx(() -> d.select(3));
            assertButtons(d, false, false, ORDER_FIXED, ORDER_FIXED);
            // x (row 1) can't go up past a, but down past b
            onFx(() -> d.select(1));
            assertButtons(d, false, true, ORDER_FIXED, "Move Down");

            // random moves (seeded) of random rows, enabled or not
            Random random = new Random(1);
            Set<List<String>> reached = new HashSet<>();
            reached.add(onFx(d::order));
            for (int step = 0; step < 150; step++) {
                int row = random.nextInt(5);
                boolean upwards = random.nextBoolean();
                onFx(() -> {
                    d.select(row);
                    (upwards ? d.up : d.down).fire();
                });
                List<String> order = onFx(d::order);
                for (List<String> pair : fixed)
                    assertTrue(order.indexOf(pair.get(0)) < order.indexOf(pair.get(1)), pair + " stays in order in " + order);
                assertEquals(expectedLocks(order, fixed), onFx(d::locked), "locks after a move, " + order);
                reached.add(order);
            }
            assertEquals(Set.of(
                    List.of("a", "x", "b", "y", "c"), List.of("a", "b", "x", "y", "c"),
                    List.of("a", "b", "y", "x", "c"), List.of("a", "b", "y", "c", "x")), reached);

            // move x to the end: the dialog returns that order
            onFx(() -> {
                while (d.order().indexOf("x") < 4) {
                    d.select(d.order().indexOf("x"));
                    d.down.fire();
                }
            });
            assertEquals(List.of("a", "b", "y", "c", "x"), texts(onFx(() -> d.dialog.getResultConverter().call(ButtonType.OK))));
            assertEquals(List.of(true, true, true, false, false), onFx(d::locked));
        }
    }

    private static List<Boolean> expectedLocks(List<String> order, Set<List<String>> fixed) {
        List<Boolean> locks = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            boolean up = i > 0 && !fixed.contains(List.of(order.get(i - 1), order.get(i)));
            boolean down = i < order.size() - 1 && !fixed.contains(List.of(order.get(i), order.get(i + 1)));
            locks.add(!up && !down);
        }
        return locks;
    }

    /** All children present are already ordered (the ambiguity lies elsewhere): only Close, a banner, no Move buttons. */
    @Test
    @Timeout(60)
    public void fullyDeterminedChildrenLeaveNothingToReorder(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "BASE, A", Map.of("f.txt", "a\nb\n"));
            commit(service, tmp.resolve("v2"), "BASE, B", Map.of("f.txt", "a\nc\n"));
            Path out = Files.createDirectories(tmp.resolve("out"));
            service.setBaseDir(out);
            Checkout checkout = service.checkout("BASE, A");
            Node.Op file = (Node.Op) fileNode(checkout.getNode(), "f.txt");
            assertEquals(List.of("a", "b"), texts(file.getChildren()));

            Fixture d = onFx(() -> new Fixture(new ReorderChildrenDialog(service, file)));
            assertEquals(List.of(ButtonType.CLOSE), onFx(() -> d.dialog.getDialogPane().getButtonTypes()));
            assertTrue(onFx(d::banner).startsWith("Nothing to reorder"), onFx(d::banner));
            assertFalse(onFx(() -> d.inLayout(d.up) || d.inLayout(d.down)), "no Move buttons");
            assertEquals(List.of(true, true), onFx(d::locked));
            assertNull(onFx(() -> d.dialog.getResultConverter().call(ButtonType.CLOSE)));
        }
    }

    /** The dialog's controls, found in its content; rows are rendered by laying the dialog pane out. */
    private static final class Fixture {
        final ReorderChildrenDialog dialog;
        final TableView<?> table;
        final Button up;
        final Button down;

        Fixture(ReorderChildrenDialog dialog) {
            this.dialog = dialog;
            this.table = (TableView<?>) find(dialog.getDialogPane().getContent(), n -> n instanceof TableView);
            // the Move buttons are in the layout only when something is reorderable
            Button[] buttons = moveButtons(dialog);
            this.up = buttons[0];
            this.down = buttons[1];
        }

        private static Button[] moveButtons(ReorderChildrenDialog dialog) {
            Button up = (Button) find(dialog.getDialogPane().getContent(), n -> n instanceof Button b && icon(b) == Feather.ARROW_UP);
            Button down = (Button) find(dialog.getDialogPane().getContent(), n -> n instanceof Button b && icon(b) == Feather.ARROW_DOWN);
            return new Button[]{up == null ? new Button() : up, down == null ? new Button() : down};
        }

        private static Ikon icon(Button button) {
            return button.getGraphic() instanceof FontIcon icon ? icon.getIconCode() : null;
        }

        boolean inLayout(Button button) {
            return find(dialog.getDialogPane().getContent(), n -> n == button) != null;
        }

        void select(int row) {
            table.getSelectionModel().select(row);
        }

        int selected() {
            return table.getSelectionModel().getSelectedIndex();
        }

        List<String> order() {
            return texts(dialog.getResultConverter().call(ButtonType.OK));
        }

        String banner() {
            Label label = (Label) find(dialog.getDialogPane().getContent(), n -> n instanceof Label l && l.getText() != null && l.getText().startsWith("Nothing to reorder"));
            return label == null ? null : label.getText();
        }

        /** Per row, whether it is shown locked (dimmed, with the order-fixed tooltip). */
        List<Boolean> locked() {
            dialog.getDialogPane().applyCss();
            dialog.getDialogPane().resize(900, 600);
            dialog.getDialogPane().layout();
            List<Boolean> locked = new ArrayList<>();
            for (int i = 0; i < table.getItems().size(); i++)
                locked.add(null);
            for (javafx.scene.Node n : findAll(table, n -> n instanceof TableRow)) {
                TableRow<?> row = (TableRow<?>) n;
                if (row.isEmpty() || row.getIndex() < 0 || row.getIndex() >= locked.size())
                    continue;
                boolean fixed = row.getTooltip() != null && ORDER_FIXED.equals(row.getTooltip().getText());
                assertEquals(fixed, row.getStyle().contains("opacity"), "dimmed exactly when locked");
                locked.set(row.getIndex(), fixed);
            }
            assertFalse(locked.contains(null), "every row rendered: " + locked);
            return locked;
        }
    }

    private static void assertButtons(Fixture d, boolean upEnabled, boolean downEnabled, String upTooltip, String downTooltip) throws Exception {
        assertEquals(List.of(upEnabled, downEnabled, upTooltip, downTooltip), onFx(() -> List.of(
                !d.up.isDisabled(), !d.down.isDisabled(), d.up.getTooltip().getText(), d.down.getTooltip().getText())));
    }

    /** Checks {@code configuration} out into {@code out} and returns the node of its single ORDER warning. */
    private static Node.Op orderWarning(EccoService service, Path out, String configuration) throws Exception {
        service.setBaseDir(Files.createDirectories(out));
        Checkout checkout = service.checkout(configuration);
        assertEquals(1, checkout.getOrderWarnings().size(), "one ambiguous order");
        return (Node.Op) checkout.getOrderWarnings().iterator().next();
    }

    private static Node fileNode(Node node, String name) {
        if (node.getArtifact() != null && String.valueOf(node.getArtifact()).startsWith(name))
            return node;
        for (Node child : node.getChildren()) {
            Node found = fileNode(child, name);
            if (found != null)
                return found;
        }
        return null;
    }

    private static List<String> texts(List<? extends Node> nodes) {
        return nodes.stream().map(n -> String.valueOf(n.getArtifact())).toList();
    }
}
