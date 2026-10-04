package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The commit comparison lists the associations of two commits side by side: one row per association,
 * under the commit (or both commits) it belongs to. Associations only in the newer commit used to be
 * listed under the older one and vice versa.
 */
public class CommitComparisonViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    @Timeout(60)
    public void eachAssociationIsListedUnderTheCommitItBelongsTo(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "BASE, A", Map.of("f.txt", "a\nb\n"));
            commit(service, tmp.resolve("v2"), "BASE, B", Map.of("f.txt", "a\nc\n"));
            Commit older = commitWith(service, "A");
            Commit newer = commitWith(service, "B");
            Association base = association(newer, "BASE");
            Association onlyOlder = association(older, "A");
            Association onlyNewer = association(newer, "B");
            assertSame(base, association(older, "BASE"), "the shared association");

            CommitComparisonView view = onFx(() -> new CommitComparisonView(service, older, newer));
            assertEquals(List.of("Commit: " + older.getId().substring(0, 9) + "...", "Commit: " + newer.getId().substring(0, 9) + "..."),
                    onFx(() -> table(view).getColumns().stream().map(TableColumn::getText).toList()));
            assertEquals(List.of(
                    List.of(base.getId(), simple(base), base.getId(), simple(base)),
                    List.of("", "", onlyNewer.getId(), simple(onlyNewer)),
                    List.of(onlyOlder.getId(), simple(onlyOlder), "", "")), onFx(() -> rows(view)));

            // full labels instead of simplified ones
            onFx(() -> ((CheckBox) find(view, n -> n instanceof CheckBox)).setSelected(false));
            assertEquals(List.of(base.getId(), full(base), base.getId(), full(base)), onFx(() -> rows(view)).get(0));
            assertNotEquals(simple(onlyNewer), full(onlyNewer), "the two label kinds differ");
            assertEquals(List.of("", "", onlyNewer.getId(), full(onlyNewer)), onFx(() -> rows(view)).get(1));

            // selecting a row of either side shows its artifacts without failing
            for (int row = 0; row < 3; row++) {
                int r = row;
                onFx(() -> table(view).getSelectionModel().select(r));
            }
            assertFalse(onFx(view::isDisabled));
        }
    }

    private static Commit commitWith(EccoService service, String feature) {
        return service.getCommits().stream()
                .filter(c -> c.getConfiguration().toString().contains(feature + "."))
                .findFirst().orElseThrow();
    }

    private static Association association(Commit commit, String feature) {
        return commit.getAssociations().stream()
                .filter(a -> simple(a).contains(feature + "."))
                .findFirst().orElseThrow();
    }

    private static String simple(Association association) {
        return association.computeCondition().getSimpleModuleRevisionConditionString();
    }

    private static String full(Association association) {
        return association.computeCondition().getModuleRevisionConditionString();
    }

    @SuppressWarnings("unchecked")
    private static TableView<Object> table(CommitComparisonView view) {
        return (TableView<Object>) find(view, n -> n instanceof TableView);
    }

    /** The cell texts of every row, left (older commit: id, condition) to right (newer commit). */
    private static List<List<String>> rows(CommitComparisonView view) {
        TableView<Object> table = table(view);
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < table.getItems().size(); i++) {
            List<String> row = new ArrayList<>();
            for (TableColumn<Object, ?> column : table.getVisibleLeafColumns())
                row.add(String.valueOf(column.getCellObservableValue(i).getValue()));
            rows.add(row);
        }
        return rows;
    }
}
