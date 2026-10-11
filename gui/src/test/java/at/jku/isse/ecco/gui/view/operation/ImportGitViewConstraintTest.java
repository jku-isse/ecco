package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import javafx.scene.Scene;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Imported commits are checked against the accepted constraints before they are committed: checked
 * afterwards, a commit is the exception to every rule it violates, and only exception-free rules
 * count - the check never found anything.
 */
public class ImportGitViewConstraintTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    @Timeout(120)
    public void anAutoImportedCommitViolatingAnAcceptedConstraintIsLoggedAndSummarized(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            // an accepted constraint only counts while the commits still witness it (at least 4)
            for (int i = 0; i < 4; i++)
                commit(service, tmp.resolve("seed" + i), "BASE, A", Map.of("seed.txt", "seed " + i + "\n"));
            service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "A", null);
            Path clone = gitHistoryWithoutA(tmp.resolve("clone"));
            List<GitCommitInfo> oldestFirst = new ArrayList<>(new GitHistoryReader().listCommits(clone));
            Collections.reverse(oldestFirst);

            List<List<String>> summaries = new CopyOnWriteArrayList<>();
            ImportGitView view = onFx(() -> {
                ImportGitView v = new ImportGitView(service) {
                    @Override
                    void reportConstraintViolations(List<String> violations) {
                        summaries.add(List.copyOf(violations));
                    }
                };
                new Scene(v);
                // no review within the range: both commits are imported unattended, with their .config
                v.startImport(clone, oldestFirst, false, 100);
                return v;
            });
            waitUntil("the import", () -> service.getCommits().size() == 6 && view.headerLabel.getText().isEmpty());
            waitUntil("the summary", () -> !summaries.isEmpty());

            // the first import is the counterexample after which A is no longer mandatory
            assertEquals(1, summaries.size());
            assertEquals(1, summaries.get(0).size(), String.valueOf(summaries.get(0)));
            assertTrue(summaries.get(0).get(0).startsWith("MANDATORY A violated"), summaries.get(0).get(0));
            String log = onFx(() -> view.logArea.getText());
            assertTrue(log.contains("CONSTRAINT: " + oldestFirst.get(0).getShortId() + ": MANDATORY A violated"), log);
        }
    }

    /** two commits, each with a .config "BASE" */
    private static Path gitHistoryWithoutA(Path dir) throws Exception {
        Files.createDirectories(dir);
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            Files.writeString(dir.resolve(".config"), "BASE");
            Files.writeString(dir.resolve("a.txt"), "a\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("first").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
            Files.writeString(dir.resolve("b.txt"), "b\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("second").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
        }
        return dir;
    }
}
