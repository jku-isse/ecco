package at.jku.isse.ecco.storage.xml.test;

import at.jku.isse.ecco.EccoService;
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
        service.getRepository();
        System.out.println("Repository loaded.");


        service.close();
        System.out.println("Repository closed.");

    }
}
