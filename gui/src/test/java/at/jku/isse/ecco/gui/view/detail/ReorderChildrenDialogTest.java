package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableCell;
import javafx.scene.control.CheckBox;
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

            Fixture d = fixture(() -> new ReorderChildrenDialog(service, file));
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
     * A large file shows the undecided rows only, with three rows around each - its places in the #
     * column - "Next undecided" goes to them, and the rows move as before; unticked, all rows are
     * there. Every row of the file was listed: for a file of thousands of lines, the few to decide
     * were to be found among them.
     */
    @Test
    @Timeout(60)
    public void aLargeFileShowsTheUndecidedRowsOnly(@TempDir Path tmp) throws Exception {
        StringBuilder before = new StringBuilder(), after = new StringBuilder();
        for (int i = 1; i <= 30; i++)
            before.append("l").append(i).append("\n");
        for (int i = 31; i <= 60; i++)
            after.append("l").append(i).append("\n");
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "BASE, A", Map.of("f.txt", before + "a\n" + after));
            commit(service, tmp.resolve("v2"), "BASE, B", Map.of("f.txt", before + "c\n" + after));
            Node.Op file = orderWarning(service, tmp.resolve("out"), "BASE, A, B");

            Fixture d = fixture(() -> new ReorderChildrenDialog(service, file));
            CheckBox undecidedOnly = onFx(() -> (CheckBox) find(d.dialog.getDialogPane().getContent(), n -> n instanceof CheckBox c && c.getText().startsWith("Undecided only")));
            assertTrue(onFx(undecidedOnly::isSelected), "on for a large file");
            List<String> shown = onFx(d::shownLabels);
            assertEquals(List.of("l28", "l29", "l30", shown.get(3), shown.get(4), "l31", "l32", "l33"), shown);
            assertEquals(java.util.Set.of("a", "c"), java.util.Set.of(shown.get(3), shown.get(4)));
            assertEquals(List.of("28", "29", "30", "31", "32", "33", "34", "35"), onFx(d::shownPlaces), "the places in the file");

            onFx(() -> d.select(0));
            onFx(() -> button(d.dialog.getDialogPane().getContent(), "Next undecided").fire());
            assertEquals(shown.get(3), onFx(d::selectedLabel));
            onFx(() -> d.down.fire());
            assertEquals(shown.get(3), onFx(() -> d.order().get(31)), "moved, filtered as it is");
            assertEquals(shown.get(3), onFx(d::selectedLabel), "still selected");

            onFx(() -> undecidedOnly.setSelected(false));
            assertEquals(62, (int) onFx(() -> d.table.getItems().size()), "all rows");
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

            Fixture d = fixture(() -> new ReorderChildrenDialog(service, file));
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

    /**
     * The order graph is read in the background, holding the service's lock: while a commit holds it,
     * the dialog opens at once, says it reads the graph and can only be cancelled - the FX thread does
     * not wait for the lock (it asked the graph there, read while a commit changed it); released, the
     * dialog is built with what the graph fixes.
     */
    @Test
    @Timeout(60)
    public void theOrderGraphIsReadOffTheFxThreadHoldingTheLock(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "BASE, A", Map.of("f.txt", "a\nb\n"));
            commit(service, tmp.resolve("v2"), "BASE, B", Map.of("f.txt", "a\nc\n"));
            Node.Op file = orderWarning(service, tmp.resolve("out"), "BASE, A, B");

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
                ReorderChildrenDialog dialog = onFx(() -> new ReorderChildrenDialog(service, file));
                Thread.sleep(300);
                assertFalse(onFx(dialog::isBuilt), "not read while the lock is held");
                assertEquals(List.of(ButtonType.CANCEL), onFx(() -> List.copyOf(dialog.getDialogPane().getButtonTypes())));
                assertEquals("Reading the order graph...", onFx(() -> ((Label) dialog.getDialogPane().getContent()).getText()));

                release.countDown();
                waitUntil("built once the lock is released", dialog::isBuilt);
                Fixture d = onFx(() -> new Fixture(dialog));
                assertEquals(List.of(ButtonType.OK, ButtonType.CANCEL), onFx(() -> d.dialog.getDialogPane().getButtonTypes()));
                assertEquals(List.of(true, false, false), onFx(d::locked));
            } finally {
                release.countDown();
                commitLike.join(10_000);
            }
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

            Fixture d = fixture(() -> new ReorderChildrenDialog(service, file));
            assertEquals(List.of(ButtonType.CLOSE), onFx(() -> d.dialog.getDialogPane().getButtonTypes()));
            assertTrue(onFx(d::banner).startsWith("Nothing to reorder"), onFx(d::banner));
            assertFalse(onFx(() -> d.inLayout(d.up) || d.inLayout(d.down)), "no Move buttons");
            assertEquals(List.of(true, true), onFx(d::locked));
            assertNull(onFx(() -> d.dialog.getResultConverter().call(ButtonType.CLOSE)));
        }
    }

    /** The dialog's controls, found in its content; rows are rendered by laying the dialog pane out. */
    /**
     * "Suggest order": the suggester gets the window of rows around those that may move, marked so,
     * and its order is put into the table - but never across an order a commit fixed: a suggester
     * reversing everything gets a b y c x, the fixed a < b < y < c and a < x kept. Nothing is
     * recorded before OK.
     */
    @Test
    @Timeout(60)
    public void aSuggestedOrderIsShownAndTheFixedOrdersWin(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "BASE, A", Map.of("f.txt", "a\nb\nc\n"));
            commit(service, tmp.resolve("v2"), "BASE, B", Map.of("f.txt", "a\nx\n"));
            commit(service, tmp.resolve("v3"), "BASE, A, C", Map.of("f.txt", "a\nb\ny\nc\n"));
            Node.Op file = orderWarning(service, tmp.resolve("out"), "BASE, A, B, C");
            List<List<at.jku.isse.ecco.service.llm.LlmOrderSuggestionClient.Row>> asked = new java.util.concurrent.CopyOnWriteArrayList<>();
            ReorderChildrenDialog.OrderSuggester reversing = (path, rows) -> {
                asked.add(rows);
                List<Integer> reversed = new ArrayList<>();
                for (int i = rows.size() - 1; i >= 0; i--)
                    reversed.add(i);
                return new at.jku.isse.ecco.service.llm.LlmOrderSuggestionClient.Suggestion(reversed, null);
            };

            Fixture d = fixture(() -> new ReorderChildrenDialog(service, file, reversing));
            onFx(() -> suggestButton(d.dialog).fire());
            waitUntil("the suggestion", () -> status(d.dialog).startsWith("Suggested"));

            assertEquals(List.of("a", "b", "y", "c", "x"), onFx(d::order));
            assertEquals(1, asked.size());
            assertEquals(List.of("a", "x", "b", "y", "c"), asked.get(0).stream().map(at.jku.isse.ecco.service.llm.LlmOrderSuggestionClient.Row::text).toList());
            assertEquals(List.of(false, true, true, false, false), asked.get(0).stream().map(at.jku.isse.ecco.service.llm.LlmOrderSuggestionClient.Row::movable).toList());
            assertEquals(List.of("a", "x", "b", "y", "c"), texts(file.getChildren()), "nothing recorded before OK");
            assertNull(onFx(() -> d.dialog.getResultConverter().call(ButtonType.CANCEL)));
        }
    }

    /**
     * The LLM gets the source a row stands for, whatever the adapter: TypeScript's from its renderer
     * (a declaration's label is only "const"), a Markdown block's from its lines (its label is
     * "(paragraph)") - and the rows show the same.
     */
    @Test
    @Timeout(120)
    public void theSuggestionIsAskedWithTheRowsSource(@TempDir Path tmp) throws Exception {
        List<String> ts = askedTexts(tmp.resolve("ts"), "app.ts", "function run(): void {\n  init();\n%s  done();\n}\n",
                "  const a = load();\n", "  console.log(\"b\");\n");
        assertTrue(ts.contains("const a = load();"), ts.toString());
        assertTrue(ts.contains("console.log(\"b\");"), ts.toString());

        List<String> md = askedTexts(tmp.resolve("md"), "Doc.md", "# Title\n\nIntro paragraph.\n\n%s## End\n\nBye.\n",
                "A paragraph about apples.\n\n", "- bullet from B\n- second bullet\n\n");
        assertTrue(md.contains("A paragraph about apples."), md.toString());
        assertTrue(md.stream().anyMatch(t -> t.equals("- bullet from B - second bullet")), md.toString());
        assertTrue(md.stream().noneMatch(t -> t.startsWith("(")), md.toString());
    }

    /** The row texts asked for the ORDER warning of [file] when variants A and B insert [a] and [b] at %s of [template]. */
    private static List<String> askedTexts(Path tmp, String file, String template, String a, String b) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "BASE", Map.of(file, String.format(template, "")));
            commit(service, tmp.resolve("v2"), "BASE, A", Map.of(file, String.format(template, a)));
            commit(service, tmp.resolve("v3"), "BASE, B", Map.of(file, String.format(template, b)));
            service.setBaseDir(Files.createDirectories(tmp.resolve("out")));
            Checkout checkout = service.checkout("BASE, A, B");
            // the warning with the open rows (Markdown also reports one fully determined inside the paragraph)
            Node.Op warning = (Node.Op) checkout.getOrderWarnings().stream()
                    .filter(n -> at.jku.isse.ecco.tree.ArtifactDiagnostics.suggestOrderFix(n).startsWith("the order"))
                    .findFirst().orElseThrow(() -> new AssertionError("no open order in " + file));
            List<List<at.jku.isse.ecco.service.llm.LlmOrderSuggestionClient.Row>> asked = new java.util.concurrent.CopyOnWriteArrayList<>();
            ReorderChildrenDialog.OrderSuggester recording = (path, rows) -> {
                asked.add(rows);
                return new at.jku.isse.ecco.service.llm.LlmOrderSuggestionClient.Suggestion(null, "recorded");
            };
            Fixture d = fixture(() -> new ReorderChildrenDialog(service, warning, recording));
            onFx(() -> suggestButton(d.dialog).fire());
            waitUntil("the question", () -> !asked.isEmpty());
            return asked.get(0).stream().map(at.jku.isse.ecco.service.llm.LlmOrderSuggestionClient.Row::text).toList();
        }
    }

    /** A failed suggestion is reported and changes nothing; without a model the button is off, with the reason next to it. */
    @Test
    @Timeout(60)
    public void aFailedSuggestionChangesNothingAndNoModelDisablesIt(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "BASE, A", Map.of("f.txt", "a\nb\n"));
            commit(service, tmp.resolve("v2"), "BASE, B", Map.of("f.txt", "a\nc\n"));
            Node.Op file = orderWarning(service, tmp.resolve("out"), "BASE, A, B");

            Fixture failing = fixture(() -> new ReorderChildrenDialog(service, file,
                    (path, rows) -> new at.jku.isse.ecco.service.llm.LlmOrderSuggestionClient.Suggestion(null, "the endpoint is offline")));
            onFx(() -> suggestButton(failing.dialog).fire());
            waitUntil("the failure", () -> status(failing.dialog).startsWith("No suggestion"));
            assertTrue(onFx(() -> status(failing.dialog)).contains("the endpoint is offline"));
            assertEquals(List.of("a", "c", "b"), onFx(failing::order));
            assertFalse(onFx(() -> suggestButton(failing.dialog).isDisabled()), "it can be asked again");

            Fixture noModel = fixture(() -> new ReorderChildrenDialog(service, file, null));
            assertTrue(onFx(() -> suggestButton(noModel.dialog).isDisabled()));
            assertTrue(onFx(() -> status(noModel.dialog)).contains("choose a model in Preferences"), onFx(() -> status(noModel.dialog)));
        }
    }

    /** While the LLM is asked, the button stops it (a slow model seemed to hang the dialog); stopped, nothing changes. */
    @Test
    @Timeout(60)
    public void aSuggestionCanBeStopped(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "BASE, A", Map.of("f.txt", "a\nb\n"));
            commit(service, tmp.resolve("v2"), "BASE, B", Map.of("f.txt", "a\nc\n"));
            Node.Op file = orderWarning(service, tmp.resolve("out"), "BASE, A, B");
            java.util.concurrent.CountDownLatch interrupted = new java.util.concurrent.CountDownLatch(1);
            ReorderChildrenDialog.OrderSuggester never = (path, rows) -> {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
                return new at.jku.isse.ecco.service.llm.LlmOrderSuggestionClient.Suggestion(List.of(1, 0, 2), null);
            };

            Fixture d = fixture(() -> new ReorderChildrenDialog(service, file, never));
            onFx(() -> suggestButton(d.dialog).fire());
            Button stop = onFx(() -> suggestButton(d.dialog));
            assertEquals("Stop", onFx(stop::getText), "the button stops it while it waits");
            assertTrue(onFx(() -> status(stop)).startsWith("Asking the LLM"), onFx(() -> status(stop)));

            onFx(stop::fire);
            assertTrue(interrupted.await(10, java.util.concurrent.TimeUnit.SECONDS), "the request is interrupted");
            assertEquals("Suggest order", onFx(stop::getText));
            assertTrue(onFx(() -> status(d.dialog)).startsWith("Stopped"));
            Thread.sleep(300);
            assertEquals(List.of("a", "c", "b"), onFx(d::order), "the order is unchanged");
        }
    }

    /** The text under the button. */
    private static String status(Button button) {
        for (javafx.scene.Node sibling : button.getParent().getChildrenUnmodifiable())
            if (sibling instanceof Label label)
                return label.getText() == null ? "" : label.getText();
        return "";
    }

    private static Button suggestButton(ReorderChildrenDialog dialog) {
        // by its icon: while it waits, its text is "Stop"
        return (Button) find(dialog.getDialogPane().getContent(), n -> n instanceof Button b && Fixture.icon(b) == Feather.ZAP);
    }

    /** The text under "Suggest order". */
    private static String status(ReorderChildrenDialog dialog) {
        javafx.scene.Node button = suggestButton(dialog);
        for (javafx.scene.Node sibling : button.getParent().getChildrenUnmodifiable())
            if (sibling instanceof Label label)
                return label.getText() == null ? "" : label.getText();
        return "";
    }

    /** The dialog [create] makes on the FX thread, once it is built: the order graph is read in the background. */
    private static Fixture fixture(java.util.concurrent.Callable<ReorderChildrenDialog> create) throws Exception {
        ReorderChildrenDialog dialog = onFx(create);
        waitUntil("the dialog is built", dialog::isBuilt);
        return onFx(() -> new Fixture(dialog));
    }

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

        String selectedLabel() {
            int index = table.getSelectionModel().getSelectedIndex();
            return String.valueOf(table.getColumns().get(1).getCellObservableValue(index).getValue());
        }

        /** The Child column of the rows shown. */
        List<String> shownLabels() {
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < table.getItems().size(); i++)
                labels.add(String.valueOf(table.getColumns().get(1).getCellObservableValue(i).getValue()));
            return labels;
        }

        /** The # column of the rows shown, as rendered. */
        List<String> shownPlaces() {
            dialog.getDialogPane().applyCss();
            dialog.getDialogPane().resize(900, 600);
            dialog.getDialogPane().layout();
            java.util.TreeMap<Integer, String> places = new java.util.TreeMap<>();
            for (javafx.scene.Node n : findAll(table, n -> n instanceof TableCell<?, ?> c && c.getTableColumn() == table.getColumns().get(0))) {
                TableCell<?, ?> cell = (TableCell<?, ?>) n;
                if (!cell.isEmpty() && cell.getIndex() >= 0 && cell.getIndex() < table.getItems().size())
                    places.put(cell.getIndex(), cell.getText());
            }
            return new ArrayList<>(places.values());
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

    /** The lines' texts - not a file's final newline, which the dialog does not offer to move. */
    private static List<String> texts(List<? extends Node> nodes) {
        return nodes.stream().filter(n -> !at.jku.isse.ecco.adapter.dispatch.TextFileFormat.isMarker(n)).map(n -> String.valueOf(n.getArtifact())).toList();
    }
}
