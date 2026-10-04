package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Variants tab decides: which variants it lists (and which commits match each), which ones
 * the toolbar actions remove, check out or edit, and where a checkout of several variants goes.
 */
public class VariantsViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    @Timeout(60)
    public void listsEveryVariantWithTheCommitsOfItsConfiguration(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a\n", "b.txt", "b\n"));
            VariantsView view = onFx(() -> new VariantsView(service));
            waitUntil("both variants with their matching commits", () -> view.variantsDataSelected.size() == 2
                    && view.variantsDataSelected.stream().noneMatch(i -> i.getMatchingCommits().isEmpty()));

            for (VariantsView.VariantsInfo info : onFx(() -> List.copyOf(view.variantsDataSelected))) {
                String expected = service.getCommits().stream()
                        .filter(c -> c.getConfiguration().equals(info.getVariant().getConfiguration()))
                        .map(Commit::getId).collect(Collectors.joining(", "));
                assertEquals(expected, info.getMatchingCommits(), "the commit that created " + info.getVariant().getConfiguration());
                assertFalse(info.isSelected(), "nothing is selected to begin with");
            }
            assertEquals(Set.of(Set.of("A.1"), Set.of("A.1", "B.1")),
                    onFx(() -> view.variantsDataSelected.stream().map(i -> revisions(i.getVariant())).collect(Collectors.toSet())));
        }
    }

    @Test
    @Timeout(60)
    public void selectAllAndUnselectAllMarkEveryVariant(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b\n"));
            VariantsView view = loaded(service, 2);

            onFx(() -> button(view, "Select All").fire());
            assertTrue(onFx(() -> view.variantsDataSelected.stream().allMatch(VariantsView.VariantsInfo::isSelected)));
            onFx(() -> button(view, "Unselect All").fire());
            assertTrue(onFx(() -> view.variantsDataSelected.stream().noneMatch(VariantsView.VariantsInfo::isSelected)));
        }
    }

    @Test
    @Timeout(60)
    public void removeTakesOnlyTheSelectedVariantsOutOfTheRepository(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b\n"));
            commit(service, tmp.resolve("v3"), "C.1", Map.of("c.txt", "c\n"));
            VariantsView view = loaded(service, 3);

            onFx(() -> {
                select(view, "B.1");
                select(view, "C.1");
                button(view, "Remove Variant Selected").fire();
            });
            waitUntil("the list shows what is left", () -> view.variantsDataSelected.size() == 1);

            assertEquals(Set.of(Set.of("A.1")), service.getRepository().getVariants().stream().map(VariantsViewTest::revisions).collect(Collectors.toSet()));
            assertEquals(Set.of("A.1"), onFx(() -> revisions(view.variantsDataSelected.get(0).getVariant())));
            assertFalse(onFx(() -> toolBar(view).isDisable()));
        }
    }

    @Test
    @Timeout(60)
    public void checkoutPutsEachSelectedVariantIntoItsOwnFolderBelowTheBaseDirectory(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a\n", "b.txt", "b\n"));
            commit(service, tmp.resolve("v3"), "C.1", Map.of("c.txt", "c\n"));
            VariantsView view = loaded(service, 3);
            // every committed variant is named "Commit": the names alone would send both checkouts into one folder
            assertEquals(Set.of("Commit"), service.getRepository().getVariants().stream().map(Variant::getName).collect(Collectors.toSet()));
            Path base = Files.createDirectories(tmp.resolve("out"));

            onFx(() -> {
                baseDirField(view).setText(base.toString());
                select(view, "A.1");
                select(view, "A.1", "B.1");
                button(view, "Checkout").fire();
            });

            List<Path> folders;
            try (Stream<Path> list = Files.list(base)) {
                folders = list.sorted().toList();
            }
            assertEquals(2, folders.size(), "one folder per selected variant: " + folders);
            Set<Set<String>> checkedOut = folders.stream().map(VariantsViewTest::files).collect(Collectors.toSet());
            assertEquals(Set.of(Set.of("a.txt"), Set.of("a.txt", "b.txt")), checkedOut);
            Set<String> ids = service.getRepository().getVariants().stream().map(Variant::getId).collect(Collectors.toSet());
            for (Path folder : folders)
                assertTrue(ids.contains(folder.getFileName().toString()), "named after the variant's id: " + folder);
            assertFalse(onFx(() -> toolBar(view).isDisable()));
        }
    }

    @Test
    @Timeout(60)
    public void checkoutUsesTheVariantNameAsFolderWhenItIsUnique(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            Variant variant = service.getRepository().getVariants().iterator().next();
            service.updateVariant(variant.getConfiguration(), "alpha", variant.getId());
            VariantsView view = onFx(() -> new VariantsView(service));
            waitUntil("the renamed variant", () -> view.variantsDataSelected.size() == 1
                    && "alpha".equals(view.variantsDataSelected.get(0).getVariant().getName()));
            Path base = Files.createDirectories(tmp.resolve("out"));

            onFx(() -> {
                baseDirField(view).setText(base.toString());
                select(view, "A.1");
                button(view, "Checkout").fire();
            });

            assertEquals("a\n", Files.readString(base.resolve("alpha").resolve("a.txt")));
        }
    }

    @Test
    @Timeout(60)
    public void aFailingCheckoutLeavesTheToolbarUsable(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            VariantsView view = loaded(service, 1);
            Path base = Files.createDirectories(tmp.resolve("out"));
            String folder = onFx(() -> view.variantsDataSelected.get(0).getVariant().getName());
            // a checkout is already there: the second one refuses to overwrite its .config
            service.setBaseDir(Files.createDirectories(base.resolve(folder)));
            service.checkout("A.1");
            int windows = onFx(() -> javafx.stage.Window.getWindows().size());

            onFx(() -> {
                baseDirField(view).setText(base.toString());
                select(view, "A.1");
                button(view, "Checkout").fire();
            });

            assertFalse(onFx(() -> toolBar(view).isDisable()), "the toolbar is enabled again");
            // the failure is shown (non-modal); close it again
            onFx(() -> {
                List<javafx.stage.Window> shown = List.copyOf(javafx.stage.Window.getWindows());
                assertEquals(windows + 1, shown.size(), "the error is shown");
                for (javafx.stage.Window window : shown)
                    if (window.getScene() != null && window.getScene().getRoot() instanceof javafx.scene.control.DialogPane)
                        window.hide();
            });
        }
    }

    @Test
    @Timeout(60)
    public void searchListsOnlyTheVariantsWithThatFeatureRevision(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a\n", "b.txt", "b\n"));
            commit(service, tmp.resolve("v3"), "B.1, C.1", Map.of("b.txt", "b\n", "c.txt", "c\n"));
            VariantsView view = loaded(service, 3);

            onFx(() -> {
                searchField(view).setText("B.1");
                button(view, "Search Feature Revision").fire();
            });
            waitUntil("the search result", () -> view.variantsDataSelected.size() == 2 && !toolBar(view).isDisable());
            assertEquals(Set.of(Set.of("A.1", "B.1"), Set.of("B.1", "C.1")),
                    onFx(() -> view.variantsDataSelected.stream().map(i -> revisions(i.getVariant())).collect(Collectors.toSet())));
        }
    }

    @Test
    @Timeout(60)
    public void addFeatureRevisionExtendsOnlyTheSelectedVariants(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "B.1", Map.of("b.txt", "b\n"));
            commit(service, tmp.resolve("v3"), "C.1", Map.of("c.txt", "c\n"));
            VariantsView view = loaded(service, 3);

            onFx(() -> {
                select(view, "A.1");
                searchField(view).setText("C.1");
                button(view, "Add Feature Revision").fire();
            });
            waitUntil("the edited variant", () -> !toolBar(view).isDisable() && view.variantsDataSelected.size() == 3
                    && view.variantsDataSelected.stream().anyMatch(i -> revisions(i.getVariant()).equals(Set.of("A.1", "C.1"))));

            assertEquals(Set.of(Set.of("A.1", "C.1"), Set.of("B.1"), Set.of("C.1")), variantRevisions(service));
        }
    }

    @Test
    @Timeout(60)
    public void removeFeatureRevisionDropsItFromTheSelectedVariants(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1, B.1", Map.of("a.txt", "a\n", "b.txt", "b\n"));
            commit(service, tmp.resolve("v2"), "B.1, C.1", Map.of("b.txt", "b\n", "c.txt", "c\n"));
            VariantsView view = loaded(service, 2);

            onFx(() -> {
                select(view, "A.1", "B.1");
                searchField(view).setText("B.1");
                button(view, "Remove Feature Revision").fire();
            });
            waitUntil("the edited variant", () -> !toolBar(view).isDisable() && view.variantsDataSelected.size() == 2
                    && view.variantsDataSelected.stream().anyMatch(i -> revisions(i.getVariant()).equals(Set.of("A.1"))));

            assertEquals(Set.of(Set.of("A.1"), Set.of("B.1", "C.1")), variantRevisions(service));
        }
    }

    @Test
    @Timeout(60)
    public void updateFeatureRevisionReplacesTheSearchedRevision(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1, B.1", Map.of("a.txt", "a\n", "b.txt", "b\n"));
            commit(service, tmp.resolve("v2"), "A.2", Map.of("a.txt", "a2\n"));
            VariantsView view = loaded(service, 2);

            onFx(() -> {
                select(view, "A.1", "B.1");
                searchField(view).setText("A.1");
                updateField(view).setText("A.2");
                button(view, "Update Feature Revision").fire();
            });
            waitUntil("the edited variant", () -> !toolBar(view).isDisable() && view.variantsDataSelected.size() == 2
                    && view.variantsDataSelected.stream().anyMatch(i -> revisions(i.getVariant()).equals(Set.of("A.2", "B.1"))));

            assertEquals(Set.of(Set.of("A.2", "B.1"), Set.of("A.2")), variantRevisions(service));
        }
    }

    @Test
    @Timeout(60)
    public void aClosedRepositoryEmptiesAndDisablesTheView(@TempDir Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        try {
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            VariantsView view = loaded(service, 1);
            assertFalse(onFx(view::isDisable));

            service.close();
            waitUntil("the view is emptied", () -> view.isDisable() && view.variantsDataSelected.isEmpty());
        } finally {
            service.close();
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    private static VariantsView loaded(EccoService service, int variants) throws Exception {
        VariantsView view = onFx(() -> new VariantsView(service));
        waitUntil(variants + " variants", () -> view.variantsDataSelected.size() == variants && !toolBar(view).isDisable());
        return view;
    }

    /** Ticks the "Selected" box of the variant with exactly these feature revisions. */
    private static void select(VariantsView view, String... revisions) {
        Set<String> wanted = Set.of(revisions);
        VariantsView.VariantsInfo info = view.variantsDataSelected.stream()
                .filter(i -> revisions(i.getVariant()).equals(wanted)).findFirst().orElseThrow();
        info.setSelected(true);
    }

    private static Set<String> revisions(Variant variant) {
        return Arrays.stream(variant.getConfiguration().getFeatureRevisions())
                .map(FeatureRevision::getFeatureRevisionString).collect(Collectors.toSet());
    }

    private static Set<Set<String>> variantRevisions(EccoService service) {
        return service.getRepository().getVariants().stream().map(VariantsViewTest::revisions).collect(Collectors.toSet());
    }

    private static Set<String> files(Path folder) {
        try (Stream<Path> list = Files.list(folder)) {
            // the checkout's own .config/.warnings files aside
            return list.map(p -> p.getFileName().toString()).filter(n -> !n.startsWith(".")).collect(Collectors.toSet());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static ToolBar toolBar(VariantsView view) {
        return (ToolBar) view.getTop();
    }

    /** The toolbar's text fields, in order: base directory, search, update. */
    private static TextField textField(VariantsView view, int index) {
        List<Node> fields = toolBar(view).getItems().stream().filter(n -> n instanceof TextField).toList();
        return (TextField) fields.get(index);
    }

    private static TextField baseDirField(VariantsView view) {
        return textField(view, 0);
    }

    private static TextField searchField(VariantsView view) {
        return textField(view, 1);
    }

    private static TextField updateField(VariantsView view) {
        return textField(view, 2);
    }
}
