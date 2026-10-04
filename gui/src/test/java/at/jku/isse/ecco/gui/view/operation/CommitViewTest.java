package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * "Commit Multiple Versions": the subfolders of a parent folder are offered for selection, each
 * chosen one is listed with the configuration from its own .config, and Commit commits them one
 * after the other in that order, each with its row's (possibly edited) configuration and message.
 * Constraint violations are asked about before anything is committed; a folder that fails stops
 * the run, while the folders committed before it stay committed.
 */
public class CommitViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    /** A CommitView whose subfolder checklist and constraint question are answered by the test. */
    private static class AnsweredCommitView extends CommitView {
        final List<List<Path>> offered = new CopyOnWriteArrayList<>();
        final List<String> askedAbout = new CopyOnWriteArrayList<>();
        volatile List<Path> choice = List.of();
        volatile boolean commitAnyway = false;

        AnsweredCommitView(EccoService service) {
            super(service);
        }

        @Override
        List<Path> chooseSubfolders(Path parent, List<Path> subfolders) {
            this.offered.add(List.copyOf(subfolders));
            return this.choice;
        }

        @Override
        boolean confirmCommitAnyway(String violations) {
            this.askedAbout.add(violations);
            return this.commitAnyway;
        }
    }

    @Test
    @Timeout(60)
    public void offersTheVisibleSubfoldersSortedAndListsTheChosenOnesWithTheirConfiguration(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            Path parent = tmp.resolve("variants");
            Path v1 = variant(parent, "v1", "BASE, A", "a\n");
            Path v2 = variant(parent, "v2", "BASE, B", "b\n");
            Path v3 = variant(parent, "v3", null, "c\n");
            Files.createDirectories(parent.resolve(".hidden"));
            Files.writeString(parent.resolve("notes.txt"), "not a folder\n");
            Shown<AnsweredCommitView> shown = show(() -> new AnsweredCommitView(service));
            try {
                AnsweredCommitView view = shown.view;
                view.choice = List.of(v3, v1);

                onFx(() -> view.selectFoldersUnder(parent));

                assertEquals(List.of(List.of(v1, v2, v3)), view.offered, "only visible folders, sorted by name");
                assertEquals(List.of(v3.toString(), v1.toString()), onFx(() -> folders(view)), "the chosen folders, in the order chosen");
                assertEquals(List.of("", "BASE, A"), onFx(() -> view.folderData.stream().map(CommitView.FolderEntry::getConfiguration).toList()),
                        "each folder's configuration comes from its own .config");
                assertEquals(List.of("", "BASE, A"), onFx(() -> view.folderData.stream().map(CommitView.FolderEntry::getCommitMessage).toList()),
                        "the commit message starts out as the configuration");
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(60)
    public void aNewChoiceReplacesTheFoldersAndACancelledOneKeepsThem(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            Path parent = tmp.resolve("variants");
            Path v1 = variant(parent, "v1", "BASE", "a\n");
            Path v2 = variant(parent, "v2", "BASE", "b\n");
            Shown<AnsweredCommitView> shown = show(() -> new AnsweredCommitView(service));
            try {
                AnsweredCommitView view = shown.view;
                view.choice = List.of(v1);
                onFx(() -> view.selectFoldersUnder(parent));
                assertEquals(List.of(v1.toString()), onFx(() -> folders(view)));

                view.choice = List.of(); // cancelled, or nothing checked
                onFx(() -> view.selectFoldersUnder(parent));
                assertEquals(List.of(v1.toString()), onFx(() -> folders(view)), "a cancelled choice keeps the folders");

                view.choice = List.of(v2);
                onFx(() -> view.selectFoldersUnder(parent));
                assertEquals(List.of(v2.toString()), onFx(() -> folders(view)), "a new choice replaces the folders instead of adding to them");
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(120)
    public void commitsOneFolderAfterTheOtherWithEachRowsConfigurationAndMessage(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            Path parent = tmp.resolve("variants");
            Path v1 = variant(parent, "v1", "BASE", "base\n");
            Path v2 = variant(parent, "v2", "BASE, A", "base\na\n");
            Path v3 = variant(parent, "v3", "BASE, C", "base\nc\n");
            Shown<AnsweredCommitView> shown = show(() -> new AnsweredCommitView(service));
            try {
                AnsweredCommitView view = shown.view;
                view.choice = List.of(v1, v2, v3);
                onFx(() -> view.selectFoldersUnder(parent));
                onFx(() -> {
                    // edited in the table: the edits are committed, not the .config files
                    view.folderData.get(1).setConfiguration("BASE, B");
                    view.folderData.get(1).setCommitMessage("second variant");
                    // a cleared configuration falls back to the folder's .config
                    view.folderData.get(2).setConfiguration("");
                });

                onFx(() -> button(view, "Commit").fire());
                waitUntil("the commits", () -> hasButton(view, "Done"));

                assertTrue(onFx(() -> view.toolBar.getStyleClass().contains("success")));
                List<Commit> commits = new ArrayList<>(service.getCommits());
                assertEquals(3, commits.size());
                assertEquals(List.of("BASE", "second variant", "BASE, C"), commits.stream().map(Commit::getCommitMessage).toList());
                assertEquals(Set.of("BASE"), features(commits.get(0)));
                assertEquals(Set.of("BASE", "B"), features(commits.get(1)));
                assertEquals(Set.of("BASE", "C"), features(commits.get(2)));
                assertEquals(List.of(), view.askedAbout, "no constraint violations, no question");

                String log = onFx(() -> view.logArea.getText());
                int first = log.indexOf("Committed v1 in ");
                int second = log.indexOf("Committed v2 in ");
                int third = log.indexOf("Committed v3 in ");
                assertTrue(first >= 0 && first < second && second < third, log);
                assertTrue(log.contains("Read file.txt using ("), log);
                assertTrue(onFx(() -> splitItems(view).stream().anyMatch(n -> n instanceof CommitDetailView)), "the last commit is shown");
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(120)
    public void aFailingFolderStopsTheRestAndTheEarlierOnesStayCommitted(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            Path parent = tmp.resolve("variants");
            Path v1 = variant(parent, "v1", "BASE", "base\n");
            Path v2 = variant(parent, "v2", null, "no configuration\n");
            Path v3 = variant(parent, "v3", "BASE, C", "base\nc\n");
            Shown<AnsweredCommitView> shown = show(() -> new AnsweredCommitView(service));
            try {
                AnsweredCommitView view = shown.view;
                view.choice = List.of(v1, v2, v3);
                onFx(() -> view.selectFoldersUnder(parent));

                onFx(() -> button(view, "Commit").fire());
                waitUntil("the commits", () -> hasButton(view, "Done"));

                assertTrue(onFx(() -> view.toolBar.getStyleClass().contains("error")));
                List<Commit> commits = new ArrayList<>(service.getCommits());
                assertEquals(1, commits.size(), "v1 stays committed, v3 is not committed");
                assertEquals(Set.of("BASE"), features(commits.get(0)));
                String log = onFx(() -> view.logArea.getText());
                assertTrue(log.contains("Committed v1 in "), log);
                assertFalse(log.contains("Committed v2") || log.contains("Committed v3"), log);
                String error = onFx(() -> splitItems(view).stream().filter(n -> n instanceof ExceptionTextArea)
                        .map(n -> ((ExceptionTextArea) n).getText()).findFirst().orElse(null));
                assertNotNull(error, "the failure is shown");
                assertTrue(error.contains("at least one feature"), error);
            } finally {
                shown.close();
            }
        }
    }

    @Test
    @Timeout(120)
    public void constraintViolationsAreAskedAboutBeforeAnythingIsCommitted(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            // an accepted constraint only counts while the commits still witness it (at least 4)
            for (int i = 0; i < 4; i++)
                commit(service, tmp.resolve("seed" + i), "BASE, A", Map.of("seed.txt", "seed " + i + "\n"));
            service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "A", null);
            Path parent = tmp.resolve("variants");
            Path ok = variant(parent, "ok", "BASE, A", "a\n");
            Path violating = variant(parent, "violating", "BASE", "base\n");
            Shown<AnsweredCommitView> shown = show(() -> new AnsweredCommitView(service));
            try {
                AnsweredCommitView view = shown.view;
                view.choice = List.of(ok, violating);
                onFx(() -> view.selectFoldersUnder(parent));
                waitUntil("the live constraint warning", () -> !view.folderData.get(1).getWarning().isEmpty());
                assertEquals("", onFx(() -> view.folderData.get(0).getWarning()));

                view.commitAnyway = false;
                onFx(() -> button(view, "Commit").fire());
                assertEquals(1, view.askedAbout.size());
                assertTrue(view.askedAbout.get(0).startsWith(violating + ": Violates accepted constraint(s)"), view.askedAbout.get(0));
                assertFalse(view.askedAbout.get(0).contains(ok + ":"), "only the violating folder is listed");
                assertEquals(4, service.getCommits().size(), "declined: nothing is committed");
                assertEquals("Folders and Configuration", onFx(() -> view.headerLabel.getText()));

                // the committed configuration is checked again after the commit, with a dialog of its own
                service.setConstraintViolationWarningsEnabled(false);
                view.commitAnyway = true;
                onFx(() -> button(view, "Commit").fire());
                waitUntil("the commits", () -> hasButton(view, "Done"));
                assertEquals(6, service.getCommits().size(), "confirmed: both folders are committed");
            } finally {
                shown.close();
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    /** a subfolder {@code name} of {@code parent} with file.txt and, unless null, a .config */
    static Path variant(Path parent, String name, String configuration, String content) throws Exception {
        Path folder = Files.createDirectories(parent.resolve(name));
        Files.writeString(folder.resolve("file.txt"), content);
        if (configuration != null)
            Files.writeString(folder.resolve(".config"), configuration);
        return folder;
    }

    private static List<String> folders(CommitView view) {
        return view.folderData.stream().map(CommitView.FolderEntry::getFolder).toList();
    }

    static Set<String> features(Commit commit) {
        return Arrays.stream(commit.getConfiguration().getFeatureRevisions())
                .map(FeatureRevision::getFeature).map(f -> f.getName()).collect(Collectors.toSet());
    }

    static boolean hasButton(Node root, String text) {
        return find(root, n -> n instanceof javafx.scene.control.Button b && text.equals(b.getText())) != null;
    }

    static List<Node> splitItems(Node root) {
        javafx.scene.control.SplitPane split = (javafx.scene.control.SplitPane) find(root, n -> n instanceof javafx.scene.control.SplitPane);
        return split == null ? List.of() : List.copyOf(split.getItems());
    }

    /** a view in its own stage, off screen: the views size their window, and some close it */
    record Shown<V extends javafx.scene.Parent>(V view, Stage stage) {
        void close() throws Exception {
            onFx(() -> this.stage.close());
        }
    }

    static <V extends javafx.scene.Parent> Shown<V> show(java.util.concurrent.Callable<V> create) throws Exception {
        return onFx(() -> {
            V view = create.call();
            Stage stage = new Stage();
            stage.setScene(new Scene(view));
            stage.setX(-10_000);
            stage.setY(-10_000);
            stage.show();
            return new Shown<>(view, stage);
        });
    }
}
