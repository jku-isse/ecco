package at.jku.isse.ecco.storage.neo4j.test;

import at.jku.isse.ecco.EccoService;
import org.junit.*;
import org.testng.annotations.AfterTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class CommitTest {

    private static final Path repoDir = Paths.get("src\\integrationTest\\data\\.ecco\\");
    private static final String testDataDir = "..\\..\\examples\\demo_variants\\V";

    @BeforeClass
    public static void prepare() {

        try {
            deleteDirectory(repoDir);
        } catch (IOException e) {
            // no such dir
        }
    }

    @AfterTest(alwaysRun = true)
    public void shutdown() {
    }

    // https://github.com/neo4j-examples/neo4j-ogm-university/blob/master/src/main/groovy/school/domain/Course.groovy
    // https://neo4j.com/docs/java-reference/current/tutorials-java-embedded/
    @Test
    public void fillRepo() throws InterruptedException {
        // create new repository
        EccoService service = new EccoService();
        service.setRepositoryDir(repoDir);
        service.init();
        System.out.println("Repository initialized.");

        //TODO: iterate directoy
        // commit all existing variants to the new repository
       for (int i = 1; i< 4; i++) {
            service.setBaseDir(Paths.get(testDataDir + i +"\\"));
            service.commit();
            System.out.println("Committed: " + "V" +i);
        }

        service.close();
        System.out.println("Repository closed.");

    }

//    @Test
//    public void loadRepo() throws InterruptedException {
//        EccoService service = new EccoService();
//        service.setRepositoryDir(repoDir);
//        service.init();
//        System.out.println("Repository initialized.");
//
//        service.
//    }

    // https://softwarecave.org/2018/03/24/delete-directory-with-contents-in-java/
    private static void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
