package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A commit of a given directory and a checkout into one leave the working directory as it is: the
 * Git import set it to a temporary directory for each commit and never set it back - after the
 * import, Status, Commit and Checkout used a deleted directory; "Checkout Selected" left it set to
 * the chosen folder, also when the checkout was cancelled.
 */
public class ExplicitDirectoryTest {

    @Test
    @Timeout(60)
    public void aCommitOfADirectoryAndACheckoutIntoOneLeaveTheWorkingDirectory(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(work);

            Path elsewhere = Files.createDirectories(tmp.resolve("elsewhere"));
            Files.writeString(elsewhere.resolve("a.txt"), "a\n");
            Commit commit = service.commitDirectory(elsewhere, "from elsewhere", "BASE");
            assertEquals(work, service.getBaseDir(), "the working directory is as it was");
            assertEquals("from elsewhere", commit.getCommitMessage());

            // its .config when no configuration is given
            Path other = Files.createDirectories(tmp.resolve("other"));
            Files.writeString(other.resolve("b.txt"), "b\n");
            Files.writeString(other.resolve(".config"), "BASE, B");
            assertTrue(service.commitDirectory(other, "with its .config", "").getConfiguration().toString().contains("B."));

            LazyCompositionRootNode root = new LazyCompositionRootNode();
            for (Association association : service.getRepository().getAssociations())
                root.addOrigNode(association.getRootNode());
            Path target = Files.createDirectories(tmp.resolve("target"));
            service.checkout(root, target);
            assertEquals("a\n", Files.readString(target.resolve("a.txt")));
            assertEquals("b\n", Files.readString(target.resolve("b.txt")));
            assertEquals(work, service.getBaseDir());
            assertFalse(Files.exists(work.resolve("a.txt")), "nothing written into the working directory");
        }
    }
}
