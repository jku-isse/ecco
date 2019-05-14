package at.jku.isse.ecco.storage.neo4j.test;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.repository.Repository;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class OpenRepoTest {

    private static final Path repoDir = Paths.get("src\\integrationTest\\data\\.ecco\\");

    @Test
    public void fillRepo() throws InterruptedException {
        // create new repository
        EccoService service = new EccoService();
        service.setRepositoryDir(repoDir);
        service.open();
        Repository repo = service.getRepository();
        System.out.println("Repository loaded.");

        repo.getFeatures();
        repo.getAssociations();


        service.close();
        System.out.println("Repository closed.");

    }
}
