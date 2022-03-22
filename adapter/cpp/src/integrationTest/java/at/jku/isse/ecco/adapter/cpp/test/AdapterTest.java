package at.jku.isse.ecco.adapter.cpp.test;


import at.jku.isse.ecco.adapter.cpp.CppReader;
import at.jku.isse.ecco.adapter.cpp.data.*;
import at.jku.isse.ecco.adapter.dispatch.DirectoryArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.util.Trees.slice;


public class AdapterTest {


    private static final Path BASE_DIR = Paths.get("C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\testadapter");
    private static final Path[] FILES = new Path[]{Paths.get("temperature.cpp")};
    private static final Path repo = Paths.get("D:\\Gabriela\\FRL-ecco\\CaseStudies\\Marlin\\variant_results");

    @Test(groups = {"integration", "java"})
    public void CPP_Adapter_Test() {
        CppReader reader = new CppReader(new MemEntityFactory());

        System.out.println("READ");
        Set<Node.Op> nodes = reader.read(BASE_DIR, FILES);

        System.out.println(nodes);
    }


    @Test(groups = {"integration", "java"})
    public void ComparisonTreesTest() throws IOException {
        CppReader reader = new CppReader(new MemEntityFactory());
        File repo = new File("D:\\Gabriela\\FRL-ecco\\CaseStudies\\SQLite\\variant_results");
        System.out.println("READ");
        File f = new File(repo,"featureCharacteristics");
        if (!f.exists())
            f.mkdir();
        File featureCSV = new File(f, "featurerevision.csv");
        if (!featureCSV.exists()) {
            try {
                FileWriter csvWriter = new FileWriter(featureCSV);
                List<List<String>> headerRows = Arrays.asList(
                        Arrays.asList("Feature", "includes", "defines", "functions", "fields", "blocks", "if", "for", "switch", "while", "do", "case", "problem"));
                for (List<String> rowData : headerRows) {
                    csvWriter.append(String.join(",", rowData));
                    csvWriter.append("\n");
                }
                csvWriter.flush();
                csvWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        EccoService service = new EccoService();
        service.setRepositoryDir(Paths.get(String.valueOf(repo)).resolve("repo"));
        service.open();
        Path variantsDir = Paths.get(String.valueOf(repo)).resolve("checkout");
        Collection<Path> variantsDirs = Files.list(variantsDir).collect(Collectors.toList());
        for (Path variantDir : variantsDirs) {
            Path BASE_DIR = variantDir;
            service.setBaseDir(BASE_DIR);
            Set<Node.Op> nodes =  service.readFiles();
            Map<String, Integer> output = new HashMap<>();
            output.put("includes", 0);
            output.put("defines", 0);
            output.put("functions", 0);
            output.put("fields", 0);
            output.put("blocks", 0);
            output.put("if", 0);
            output.put("for", 0);
            output.put("switch", 0);
            output.put("while", 0);
            output.put("do", 0);
            output.put("case", 0);
            output.put("problem", 0);
            for (Node n : nodes){
                try {
                    composeNodes(n,variantDir.getFileName().toString(),featureCSV,output);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            //appending to the csv
            try {
                FileWriter csvWriter = new FileWriter(featureCSV, true);
                ArrayDeque<String> values = new ArrayDeque<>();

                values.add(variantDir.getFileName().toString());
                values.add(String.valueOf(output.get("includes")));
                values.add(String.valueOf(output.get("defines")));
                values.add(String.valueOf(output.get("functions")));
                values.add(String.valueOf(output.get("fields")));
                values.add(String.valueOf(output.get("blocks")));
                values.add(String.valueOf(output.get("if")));
                values.add(String.valueOf(output.get("for")));
                values.add(String.valueOf(output.get("switch")));
                values.add(String.valueOf(output.get("while")));
                values.add(String.valueOf(output.get("do")));
                values.add(String.valueOf(output.get("case")));
                values.add(String.valueOf(output.get("problem")));

                csvWriter.append(String.join(",", values))
                        .append("\n");
                csvWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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


    @Test(groups = {"integration", "java"})
    public void FeatureRevisionCharacteristicTest() throws IOException {
        File file = new File(String.valueOf(Paths.get(repo.toUri())), "featureCharacteristics");
        if (!file.exists())
            file.mkdir();
        File featureCSV = new File(file, "featurerevision.csv");
        if (!featureCSV.exists()) {
            try {
                FileWriter csvWriter = new FileWriter(featureCSV);
                List<List<String>> headerRows = Arrays.asList(
                        Arrays.asList("Feature", "includes", "defines", "functions", "fields", "blocks", "if", "for", "switch", "while", "do", "case", "problem"));
                for (List<String> rowData : headerRows) {
                    csvWriter.append(String.join(",", rowData));
                    csvWriter.append("\n");
                }
                csvWriter.flush();
                csvWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        EccoService service = new EccoService();
        service.setRepositoryDir(repo.resolve("repo"));
        service.open();
        System.out.println(" **** Repo opened!");
        Collection<? extends Feature> featureRevisions = service.getRepository().getFeatures();

        //for (Feature feature : featureRevisions) {
        //    FeaturesView.this.featuresData.add(feature);
        //}

        /*Repository repository = service.getRepository();
        ArrayList<Feature> features = repository.getFeature();
        for (Feature feat : features) {
            Set<Node> nodes;
            for (FeatureRevision revision : feat.getRevisions()) {
                System.out.println(revision.getFeatureRevisionString());
                nodes = service.checkout2(revision.getFeatureRevisionString());
                for (Node node : nodes) {
                    composeNodes(node, revision.getFeatureRevisionString(), featureCSV);
                }
            }
        }*/
    }

    public void composeNodes(Node node, String featurerevision, File file,Map<String,Integer> output) throws IOException {
        Artifact artifact = node.getArtifact();
        if (artifact.getData() instanceof DirectoryArtifactData) {
            DirectoryArtifactData directoryArtifactData = (DirectoryArtifactData) artifact.getData();
            for (Node child : node.getChildren()) {
                composeNodes(child, featurerevision, file,output);
            }
        } else if (artifact.getData() instanceof PluginArtifactData) {
            PluginArtifactData pluginArtifactData = (PluginArtifactData) node.getArtifact().getData();

            Set<Node> pluginInput = new HashSet<>();
            pluginInput.add(node);

            for (Node no : pluginInput) {
                processNode(no,output);
            }
        }
    }


    private void processNode(Node n, Map<String,Integer> output) {
        if ((n.getArtifact().getData() instanceof PluginArtifactData)) {
            PluginArtifactData rootData = (PluginArtifactData) n.getArtifact().getData();
            final List<? extends Node> children = n.getChildren();
            if (children.size() >= 1) {

                if (n.getChildren().size() > 0) {
                    for (Node node : n.getChildren()) {
                        visitingNodes(node, output);
                    }
                }
            }
        }
    }


    public void visitingNodes(Node childNode, Map<String, Integer> featCharc) {
        if (childNode.getArtifact().toString().equals("INCLUDES") || childNode.getArtifact().toString().equals("FUNCTIONS")) {
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, featCharc);
                }
            }
        } else if (childNode.getArtifact().toString().equals("FIELDS")) {
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    featCharc.computeIfPresent("fields", (k, v) -> v + 1);
                    featCharc.computeIfAbsent("fields", v -> 1);
                }
            }
        } else if (childNode.getArtifact().toString().equals("DEFINES")) {
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    featCharc.computeIfPresent("defines", (k, v) -> v + 1);
                    featCharc.computeIfAbsent("defines", v -> 1);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof IncludeArtifactData)) {
            final IncludeArtifactData artifactData = (IncludeArtifactData) childNode.getArtifact().getData();
            featCharc.computeIfPresent("includes", (k, v) -> v + 1);
            featCharc.computeIfAbsent("includes", v -> 1);
        } else if ((childNode.getArtifact().getData() instanceof LineArtifactData)) {
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, featCharc);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof FunctionArtifactData)) {
            featCharc.computeIfPresent("functions", (k, v) -> v + 1);
            featCharc.computeIfAbsent("functions", v -> 1);
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, featCharc);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof BlockArtifactData)) {
            featCharc.computeIfPresent("blocks", (k, v) -> v + 1);
            featCharc.computeIfAbsent("blocks", v -> 1);
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, featCharc);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof DoBlockArtifactData)) {
            featCharc.computeIfPresent("do", (k, v) -> v + 1);
            featCharc.computeIfAbsent("do", v -> 1);
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, featCharc);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof ForBlockArtifactData)) {
            featCharc.computeIfPresent("for", (k, v) -> v + 1);
            featCharc.computeIfAbsent("for", v -> 1);
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, featCharc);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof IfBlockArtifactData)) {
            featCharc.computeIfPresent("if", (k, v) -> v + 1);
            featCharc.computeIfAbsent("if", v -> 1);
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, featCharc);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof ProblemBlockArtifactData)) {
            featCharc.computeIfPresent("problem", (k, v) -> v + 1);
            featCharc.computeIfAbsent("problem", v -> 1);
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, featCharc);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof SwitchBlockArtifactData)) {
            featCharc.computeIfPresent("switch", (k, v) -> v + 1);
            featCharc.computeIfAbsent("switch", v -> 1);
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, featCharc);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof WhileBlockArtifactData)) {
            featCharc.computeIfPresent("while", (k, v) -> v + 1);
            featCharc.computeIfAbsent("while", v -> 1);
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, featCharc);
                }
            }
        } else if (childNode.getArtifact().getData() instanceof CaseBlockArtifactData) {
            featCharc.computeIfPresent("case", (k, v) -> v + 1);
            featCharc.computeIfAbsent("case", v -> 1);
            if (((CaseBlockArtifactData) childNode.getArtifact().getData()).getSameline()) {
                if (childNode.getChildren().size() > 0) {
                    for (Node node : childNode.getChildren()) {
                    }
                }
            } else {
                if (childNode.getChildren().size() > 0) {
                    for (Node node : childNode.getChildren()) {
                        visitingNodes(node, featCharc);
                    }
                }
            }

        } else {
            System.out.println("*************** Forgot to treat an artificat data type");
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
