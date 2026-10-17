package at.jku.isse.ecco.gui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * How tables size their columns: a growing column fills the rows' width - it left an empty strip
 * after the last column -, headers are not cut off ("NumArtifa..."), and ids are shown by their
 * first characters instead of being cut off at the column's edge ("3511d296-...").
 */
public class TableColumnsTest {

    private static final String ID = "3511d296-6a1e-4c55-9f0e-2b8c1e7d4a90";

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @AfterEach
    public void closeWindows() throws Exception {
        onFx(() -> {
            for (Window window : List.copyOf(Window.getWindows()))
                window.hide();
        });
    }

    @Test
    public void idsAreShownByTheirFirstCharacters() {
        assertEquals("3511d296", TableColumns.shortIds(ID));
        assertEquals("3511d296, 7f1aff21", TableColumns.shortIds(ID + ", 7f1aff21-643b-4b2b-b296-c9200408b479"));
        assertEquals("short", TableColumns.shortIds("short"));
    }

    @Test
    @Timeout(60)
    public void aGrowingColumnFillsTheRowsAndHeadersAndIdsFit() throws Exception {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (int i = 0; i < 60; i++) // more than fit: a vertical scroll bar
            items.add(ID);
        TableView<String> table = onFx(() -> {
            TableView<String> view = new TableView<>(items);
            TableColumn<String, String> id = new TableColumn<>("Id");
            TableColumn<String, String> condition = new TableColumn<>("Simplified Condition");
            TableColumn<String, String> count = new TableColumn<>("NumArtifacts");
            id.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue()));
            condition.setCellValueFactory(p -> new ReadOnlyStringWrapper("A"));
            count.setCellValueFactory(p -> new ReadOnlyStringWrapper("1"));
            view.getColumns().setAll(List.of(id, condition, count));
            TableColumns.shortIds(id, items);
            TableColumns.controlWidth(count);
            TableColumns.growToFill(view, condition);
            view.getSortOrder().add(count); // its header shows the sort arrow
            Stage stage = new Stage();
            Scene scene = new Scene(view, 700, 300);
            // the application's look: Cupertino's header padding cut off "NumArtifacts"
            scene.getStylesheets().addAll(new atlantafx.base.theme.CupertinoLight().getUserAgentStylesheet(), AppStylesheet.FILE);
            stage.setScene(scene);
            stage.show();
            return view;
        });
        waitUntil("the columns fill the rows", () -> {
            Region rows = (Region) table.lookup(".clipped-container");
            double columns = table.getColumns().stream().mapToDouble(TableColumn::getWidth).sum();
            return rows != null && rows.getWidth() > 0 && Math.abs(rows.getWidth() - columns) <= 1;
        });

        Set<String> shown = onFx(() -> table.lookupAll(".table-cell").stream()
                .filter(n -> n instanceof TableCell<?, ?> c && !c.isEmpty() && c.getTableColumn().getText().equals("Id"))
                .map(n -> ((Labeled) n).getText()).collect(Collectors.toSet()));
        assertEquals(Set.of("3511d296"), shown);
        // what a header shows: its text node, "NumArtifa..." when cut off
        Set<String> headers = onFx(() -> table.lookupAll(".column-header .label").stream()
                .flatMap(n -> ((javafx.scene.Parent) n).getChildrenUnmodifiable().stream())
                .filter(n -> n instanceof javafx.scene.text.Text)
                .map(n -> ((javafx.scene.text.Text) n).getText()).collect(Collectors.toSet()));
        assertTrue(headers.contains("NumArtifacts") && headers.contains("Simplified Condition"), "headers not cut off: " + headers);
    }

    /**
     * A table whose column grows to fill it is freed with it: a static map kept every one - each
     * Commit Comparison window with its thousands of rows and details, and every dialog's.
     */
    @Test
    @org.junit.jupiter.api.Timeout(60)
    public void aTableWithAGrowingColumnIsFreedWithIt() throws Exception {
        java.lang.ref.WeakReference<javafx.scene.control.TableView<String>> reference = onFx(() -> {
            javafx.scene.control.TableView<String> table = new javafx.scene.control.TableView<>();
            javafx.scene.control.TableColumn<String, String> column = new javafx.scene.control.TableColumn<>("c");
            table.getColumns().add(column);
            TableColumns.growToFill(table, column);
            return new java.lang.ref.WeakReference<>(table);
        });
        onFx(() -> { }); // the layout pass growToFill asked for
        for (int i = 0; i < 50 && reference.get() != null; i++) {
            System.gc();
            Thread.sleep(50);
        }
        assertNull(reference.get(), "still held");
    }
}
