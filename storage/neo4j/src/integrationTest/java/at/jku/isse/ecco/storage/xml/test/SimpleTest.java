package at.jku.isse.ecco.storage.xml.test;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.storage.neo4j.impl.NeoRepository;
import at.jku.isse.ecco.storage.neo4j.impl.NeoRepositoryDao;
import at.jku.isse.ecco.storage.neo4j.impl.NeoTransactionStrategy;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.collect.Sets.powerSet;

public class SimpleTest {

    private static final Path resourceRoot = Paths.get("src/integrationTest/data/out");

    @BeforeClass
    public static void prepare() throws IOException {
        Files.createDirectories(resourceRoot);
    }


    @Test
    public void fillRepo() {
        // create new repository
        EccoService service = new EccoService();
        service.setRepositoryDir(Paths.get("G:\\Dropbox\\_uni\\_2018Ws\\Praktikum\\repo\\.ecco"));
        service.init();
        System.out.println("Repository initialized.");

        //TODO: iterate directoy
        //TODO: delete after creation
        // commit all existing variants to the new repository
       for (int i = 1; i< 10; i++) {
            service.setBaseDir(Paths.get("G:\\Dropbox\\_uni\\_2018Ws\\Praktikum\\ecco\\examples\\demo_variants\\V" + i +"\\"));
            service.commit();
            System.out.println("Committed: " + "V" +i);
        }

        // close repository
        service.close();
        System.out.println("Repository closed.");

    }


}
