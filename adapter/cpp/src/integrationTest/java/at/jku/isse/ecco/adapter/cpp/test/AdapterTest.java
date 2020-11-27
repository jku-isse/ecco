package at.jku.isse.ecco.adapter.cpp.test;


import at.jku.isse.ecco.adapter.cpp.CppReader;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Collectors;


public class AdapterTest {


    private static final Path BASE_DIR = Paths.get("C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\testadapter");
    private static final Path[] FILES = new Path[]{Paths.get("expr.c")};

    @Test(groups = {"integration", "java"})
    public void CPP_Adapter_Test() {
        CppReader reader = new CppReader(new MemEntityFactory());

        System.out.println("READ");
        Set<Node.Op> nodes = reader.read(BASE_DIR, FILES);

        System.out.println(nodes);
    }

    // set this path to a concrete scenario if you only want to run a specific one
    //private static final Path SCENARIO_DIR = Paths.get("C:\\OriginalVariant");
    private static final Path SCENARIO_DIR = Paths.get("C:\\Users\\gabil\\Desktop\\teste\\ActualECCO\\ArgoUML\\Compare_traces\\tests");
    // set this path to where the results should be stored
    private static final Path SCENARIO_OUTPUT_DIR = SCENARIO_DIR.resolve("results");

    /**
     * Creates repository in SCENARIO_OUTPUT_DIR for specific scenario in SCENARIO_DIR.
     */
    @Test(groups = {"integration", "runtime"})
    public void Create_Repo() throws IOException {
        this.createRepo(SCENARIO_DIR, SCENARIO_OUTPUT_DIR);
    }

    @BeforeTest(alwaysRun = true)
    public void beforeTest() {
        System.out.println("BEFORE");

        // configure logger
        Logger logger = Logger.getLogger("at.jku.isse.ecco");
        logger.setLevel(Level.ALL);
        for (Handler handler : logger.getHandlers())
            logger.removeHandler(handler);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        handler.setFormatter(new SimpleFormatter());
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.info("Logging to: " + Arrays.stream(logger.getHandlers()).map(Object::toString).collect(Collectors.joining(", ")));
    }

    @AfterTest(alwaysRun = true)
    public void afterTest() {
        System.out.println("AFTER");
    }

    private void createRepo(Path scenarioDir, Path scenarioOutputDir) throws IOException {
        // create new repository
        EccoService service = new EccoService();
        service.setRepositoryDir(scenarioOutputDir.resolve("repo"));
        service.init();
        //service.open();
        System.out.println("Repository initialized.");

        // commit all existing variants to the new repository
        Path variantsDir = scenarioDir.resolve("variants");
        Path configsDir = scenarioDir.resolve("configs");

        List<Long> runtimes = new ArrayList<>();
        int counter = 0;
        Collection<Path> variantsDirs = Files.list(variantsDir).collect(Collectors.toList());
        for (Path variantDir : variantsDirs) {
            Path configFile = configsDir.resolve(variantDir.getFileName());
//			// this is to avoid overheating of my laptop for large scenarios
//			try {
//				Thread.sleep(20000);
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
            long before = System.currentTimeMillis();

            System.out.println("COUNT: " + counter);
            System.out.println("Committing: " + variantDir);

            String configurationString = Files.readAllLines(configFile).stream().map(featureString -> featureString + ".1").collect(Collectors.joining(","));
            if (configurationString.isEmpty())
                configurationString = "BASE.1";
                // depending on how the configs are, if lower case then uncomment the two lines below and comment the other following two lines
                //else if(!configurationString.contains("Base"))
                //    configurationString = "Base.1," + configurationString;
            else if (!configurationString.contains("BASE"))
                configurationString = "BASE.1," + configurationString;

            System.out.println("CONFIG: " + configurationString);

            service.setBaseDir(variantDir.resolve("src"));
            service.commit(configurationString);

            System.out.println("Committed: " + variantDir);
            counter++;

            long after = System.currentTimeMillis();
            long runtime = after - before;
            runtimes.add(runtime);
            System.out.println("TIME: " + runtime + "ms");
        }

        // close repository
        service.close();
        System.out.println("Repository closed.");

        Files.write(scenarioOutputDir.resolve("time.txt"), runtimes.stream().map(Object::toString).collect(Collectors.toList()));
    }

}
