package at.jku.isse.ecco.storage.ser.core;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SerCommit.getAssociations() skipped association ids the repository could not resolve, so a
 * damaged repository showed commits with silently missing associations (commit details, commit
 * comparison, knowledge graph) - unlike every other id resolution in the ser storage, which fails.
 * Repository.extract() keeps commits' ids in step with the associations it replaces (0 dangling
 * ids in any repository built while checking this, forks and merges included), so an unresolvable
 * id is damage and must be reported.
 */
public class SerCommitDanglingAssociationTest {

    @Test
    @Timeout(60)
    public void anUnresolvableAssociationIdIsReported() throws Exception {
        Path workDir = Files.createTempDirectory("sercommit-dangling");
        Path content = Files.createDirectories(workDir.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("a.txt"), "a\n");
            Commit commit = service.commit("a", "A");

            Association association = commit.getAssociations().iterator().next();
            assertEquals(1, commit.getAssociations().size());

            // what a damaged repository looks like: the commit still names an association the
            // repository no longer has
            ((Repository.Op) service.getRepository()).removeAssociation((Association.Op) association);

            EccoException e = assertThrows(EccoException.class, commit::getAssociations);
            assertTrue(e.getMessage().contains(association.getId()), e.getMessage());
        }
    }
}
