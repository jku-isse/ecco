package at.jku.isse.ecco.adapter.cpp.test;


import at.jku.isse.ecco.adapter.cpp.CppReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.core.Association;
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

import static at.jku.isse.ecco.util.Trees.slice;


public class AdapterTest {


    private static final Path BASE_DIR = Paths.get("C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\testadapter");
    private static final Path[] FILES = new Path[]{Paths.get("output.c")};

    @Test(groups = {"integration", "java"})
    public void CPP_Adapter_Test() {
        CppReader reader = new CppReader(new MemEntityFactory());

        System.out.println("READ");
        Set<Node.Op> nodes = reader.read(BASE_DIR, FILES);

        System.out.println(nodes);
    }

    Node.Op union = null;
    Node.Op root1 = null;

    @Test(groups = {"integration", "java"})
    public void ComparisonTreesTest() {
        CppReader reader = new CppReader(new MemEntityFactory());

        System.out.println("READ");
        /*Path BASE_DIR = Paths.get("C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\CaseStudies\\Test500commits\\Marlin\\Input_variants\\CS11.1,CS10.1,TCCR1.1,BASE.52\\Marlin");
        Path[] FILES = new Path[]{Paths.get("EEPROMwrite.h")};
        Path CHECKOUT_DIR = Paths.get("C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\CaseStudies\\Test500commits\\Marlin\\variant_results\\checkoutOriginal\\CS11.1,CS10.1,TCCR1.1,BASE.52\\Marlin");
        Set<Node.Op> nodes = reader.read(BASE_DIR, FILES);
        Set<Node.Op> nodesCheckout = reader.read(CHECKOUT_DIR, FILES);
        Node.Op union = null;
        Node teste = nodes.iterator().next();
        Node.Op root1 = nodes.iterator().next();
        Node.Op root2 = nodesCheckout.iterator().next();
        union = slice(root1, root2);
        System.out.println("Total Artifacts in common: "+union.countArtifacts());
        System.out.println("Total Artifacts in input tree: "+root1.countArtifacts());
        System.out.println("Total Artifacts in checkout tree: "+root2.countArtifacts());
        int total = union.countArtifacts()+root1.countArtifacts()+root2.countArtifacts();
        System.out.println("Total Artifacts: "+total);
        */
        EccoService service = new EccoService();
        Path repo = Paths.get("C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\RunningExample\\variant_results");
        service.setRepositoryDir(repo.resolve("repo"));
        service.open();
        Path BASE_DIR = Paths.get("C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\RunningExample\\Input_variants\\BASE.1");
        Path[] FILES = new Path[]{Paths.get("connect.c")};
        reader = new CppReader(new MemEntityFactory());
        Set<Node.Op> nodes = reader.read(BASE_DIR, FILES);
        root1 = nodes.iterator().next();
        Collection<? extends Association> associations = service.getRepository().getAssociations();
        for (Association assoc : associations) {
            if (assoc.getId().equals("802f2436-9c75-4ba1-abec-147bf09a5946"))
                System.out.println("Artifacts: " + assoc.getRootNode().countArtifacts() + " " + assoc.getId());
            computeString((Node.Op) assoc.getRootNode(), root1, union);
        }
        //Set<Node> node= service.compareArtifacts("HAVE_SELECT.1,BASE.1");
        //root2 = node.iterator().next();
        //union = slice(root1, root2);

    }


    private void computeString(Node.Op node, Node.Op root1, Node.Op union) {
        if (node.getArtifact() != null && node.getArtifact().getData() != null) {
            if (node.getArtifact().getData() instanceof PluginArtifactData) {
                System.out.println(node.getArtifact().getData());
                union = slice(node, root1);
                System.out.println("Total Artifacts in common: " + union.countArtifacts());
                System.out.println("Total Artifacts in input tree: " + node.countArtifacts());
                System.out.println("Total Artifacts in checkout tree: " + root1.countArtifacts());
                int total = union.countArtifacts() + root1.countArtifacts() + node.countArtifacts();
                System.out.println("Total Artifacts: " + total);

                //currentClassName = ((ClassArtifactData) node.getArtifact().getData()).getName();

                //boolean nonMethodDescendants = this.checkNonMethodDescendants(node);

                //if (lines.containsKey(currentClassName))
                //    throw new EccoException("Class already exists.");
                //if (node.isUnique() && (!node.getParent().isUnique() || (node.getParent().getArtifact() != null && node.getArtifact().getData() != null && !(node.getParent().getArtifact().getData() instanceof ClassArtifactData)))) {
                //    sb.append(currentClassName + "\n");
                //    lines.put(currentClassName, true);
                //} else if (!node.isUnique() && nonMethodDescendants) {
                //    sb.append(currentClassName + " Refinement\n");
                //    lines.put(currentClassName, false);
                //}
            } /*else if (node.getArtifact().getData() instanceof MethodArtifactData) {
                String methodSignature = ((MethodArtifactData) node.getArtifact().getData()).getSignature().replaceAll(", ", ",");
                String fullMethodSignature = currentClassName + " " + methodSignature;
                if (lines.containsKey(fullMethodSignature))
                    throw new EccoException("Method already exists.");
                if (node.isUnique() && !node.getParent().isUnique()) {
                    sb.append(fullMethodSignature + "\n");
                    lines.put(fullMethodSignature, true);
                } else if (!node.isUnique() && !node.getChildren().isEmpty()) { // it has unique descendants
                    sb.append(fullMethodSignature + " Refinement\n");
                    lines.put(fullMethodSignature, false);
                }
                */
        }
        for (Node.Op childNode : node.getChildren()) {
            this.computeString(childNode, root1, union);
        }
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
