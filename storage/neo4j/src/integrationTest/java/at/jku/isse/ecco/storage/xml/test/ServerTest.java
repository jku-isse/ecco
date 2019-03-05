package at.jku.isse.ecco.storage.xml.test;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.storage.neo4j.NeoSessionFactory;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServerTest {
    private static final Path repoDir = Paths.get("src\\integrationTest\\data\\ecco\\");

    @Test
    public void fillRepo() throws InterruptedException, IOException {

        NeoSessionFactory nsf = new NeoSessionFactory(repoDir);
        System.out.println("\nServer started..");

        System.out.println("Waiting 15min");
        Thread.sleep(900000);

        System.out.println("Server shutdown");

    }

}
