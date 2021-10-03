package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.challenge.*;
import at.jku.isse.ecco.adapter.dispatch.DispatchReader;
import at.jku.isse.ecco.adapter.runtime.RuntimeReader;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;
import org.testng.annotations.Test;

import javax.inject.Inject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class RuntimeTest {

    //private Path BASE_DIR = Paths.get("C:\\Users\\gabil\\Desktop\\BonusWork\\Results\\test_jacoco2\\variants2\\ACTIVITYDIAGRAM.config\\src");
    //private Path BASE_DIR = Paths.get("C:\\Users\\gabil\\Desktop\\DynamicFeatureLocation\\Results\\original_with_jacoco\\variants\\COLLABORATIONDIAGRAM.config\\src");
    //private Path BASE_FEATURE = Paths.get("C:\\Users\\gabil\\Desktop\\DynamicFeatureLocation\\Results\\test-tree-comparison\\00065-collaboration.config\\src"); //05 just activity and 08 activity logging and cognitive
    private Path RUNTIME_FEATURE = Paths.get("C:\\Users\\gabil\\Desktop\\DynamicFeatureLocation\\ResultsPaper\\tests10-08\\btrace\\ExecutarFeature-depois-start-app\\runtimeCOLLABORATION\\src");
    private Path STATIC_FEATURE = Paths.get("C:\\Users\\gabil\\Desktop\\DynamicFeatureLocation\\ResultsPaper\\tests10-08\\btrace\\ExecutarFeature-depois-start-app\\staticCOLLABORATION\\src"); //05 just activity and 08 activity logging and cognitive
    private Path BASE_FEATURE = Paths.get("C:\\Users\\gabil\\Desktop\\DynamicFeatureLocation\\ResultsPaper\\tests10-08\\btrace\\ExecutarFeature-depois-start-app\\staticBASE-semfeatures\\src");
    private static final Path[] FILES = new Path[]{Paths.get("")};
    private Path repo = Paths.get("C:\\Users\\gabil\\Desktop\\DynamicFeatureLocation\\Results\\test_jacoco2\\results2\\repo");
    public final String resultMetrics_path = "C:\\Users\\gabil\\Desktop\\teste\\ActualECCO\\Method_comparison\\results";

    @Inject
    private DispatchReader dispatchReader;

    public int count = 0;
    public int countLines = 0;

    @Test(groups = {"integration", "java"})
    public void adapter_Test() throws IOException {

        RuntimeReader readerRuntime = new RuntimeReader(new MemEntityFactory());

        EccoService service = new EccoService();
        service.setRepositoryDir(repo);
        //service.init();
        service.open();


        //Path variantDir = Paths.get("C:\\Users\\gabil\\Desktop\\BonusWork\\Results\\test_jacoco2\\variants2\\ACTIVITYDIAGRAM.config");
        //Path configsDir = Paths.get("C:\\Users\\gabil\\Desktop\\BonusWork\\Results\\test_jacoco2\\configs2");

        //Path configFile = Paths.get(configsDir+"\\ACTIVITYDIAGRAM.config");
        //String configurationString = Files.readAllLines(configFile).stream().map(featureString -> featureString + ".1").collect(Collectors.joining(","));
        //if (configurationString.isEmpty())
        //     configurationString = "BASE.1";
        // else
        //     configurationString = "BASE.1," + configurationString;
        //  System.out.println("CONFIG: " + configurationString);

        // service.setBaseDir(variantDir.resolve("src"));


        Set<ArtifactReader<Path, Set<Node.Op>>> readers = new HashSet<ArtifactReader<Path, Set<Node.Op>>>();
        readers.add(readerRuntime);
        dispatchReader = new DispatchReader(service.getEntityFactory(), (Set<ArtifactReader<Path, Set<Node.Op>>>) readers, service.getRepositoryDir());

        service.setReader(dispatchReader);
        service.getReader().init();
        //service.commit(configurationString);

        // load adapter mappings from reader plugin
        for (Map.Entry<Integer, String[]> adapterPattern : readerRuntime.getPrioritizedPatterns().entrySet()) {
            String[] pair = adapterPattern.getValue();
            for (String pat : pair) {
                String pattern = pat;
                dispatchReader.addAdapterMappings(pattern, readerRuntime);
            }
        }

        Set<Node.Op> nodesRuntime = this.dispatchReader.read(this.RUNTIME_FEATURE, new Path[]{Paths.get("")});
        //Set<Node.Op> nodesRuntimeJacoco = this.dispatchReader.read(this.JACOCO_FEATURE, new Path[]{Paths.get("")});

        readers = new HashSet<ArtifactReader<Path, Set<Node.Op>>>();
        JavaChallengeReader readerChallenge = new JavaChallengeReader(new MemEntityFactory());
        readers.add(readerChallenge);
        dispatchReader = new DispatchReader(service.getEntityFactory(), (Set<ArtifactReader<Path, Set<Node.Op>>>) readers, service.getRepositoryDir());
        //service.setReader(dispatchReader);
        //service.commit(configurationString);

        // load adapter mappings from reader plugin
        for (Map.Entry<Integer, String[]> adapterPattern : readerChallenge.getPrioritizedPatterns().entrySet()) {
            String[] pair = adapterPattern.getValue();
            String pluginId = readerChallenge.getPluginId();
            for (String pat : pair) {
                String pattern = pat;
                dispatchReader.addAdapterMappings(pattern, readerChallenge);
            }
        }

        Set<Node.Op> nodesChallenge = this.dispatchReader.read(this.STATIC_FEATURE, new Path[]{Paths.get("")});
        Set<Node.Op> nodesBase = this.dispatchReader.read(this.BASE_FEATURE, new Path[]{Paths.get("")});

        Node.Op union = null;
        //compareNodes(nodesRuntime, nodesChallenge);
        for (Node.Op nodes : nodesChallenge) {
           /* Node.Op node = (Node.Op) nodes;
            Node.Op nodeRuntime = null;
            Node.Op nodeChallenge = null;
            for (Node.Op nodesruntime : nodesRuntime) {
                nodeRuntime = (Node.Op) nodesruntime;
                if (nodeRuntime.getArtifact().getData().toString().equals(node.getArtifact().getData().toString())) {
                    break;
                }
            }*/

            /*JavaChallengeReader challengeReader2 = new JavaChallengeReader(new MemEntityFactory());
            RuntimeReader runtimeReader2 = new RuntimeReader(new MemEntityFactory());
            Set<Node.Op> nodes1 = challengeReader2.read(Paths.get("C:\\Users\\gabil\\Desktop\\DynamicFeatureLocation\\Results\\btrace-01-02-2020\\config0005\\src"), new Path[]{Paths.get("")});
            Node.Op root11 = nodes1.iterator().next();
            Set<Node.Op> nodes2 = challengeReader2.read(Paths.get("C:\\Users\\gabil\\Desktop\\DynamicFeatureLocation\\Results\\btrace-01-02-2020\\config0005\\src"), new Path[]{Paths.get("")});
            Node.Op root22 = nodes2.iterator().next();
            Node.Op root3 = Trees.slice(root11, root22);
            int totalNumberOfNodes = root11.countArtifacts() + root22.countArtifacts() + root3.countArtifacts();
            int onlyInLeft = root11.countArtifacts();
            int onlyInRight = root22.countArtifacts();
            int inCommon = root3.countArtifacts();

            System.out.println("TOTAL: " + totalNumberOfNodes);
            System.out.println("LEFT: " + onlyInLeft);
            System.out.println("RIGHT: " + onlyInRight);
            System.out.println("COMMON: " + inCommon);

            */


            Node.Op root1 = nodesChallenge.iterator().next();
            Node.Op root2 = nodesRuntime.iterator().next();
            //Node.Op root3 = nodesChallenge.iterator().next();
            Node.Op rootbase = nodesBase.iterator().next();
            Node.Op union2 = Trees.slice(root1, rootbase);


            System.out.println("Total Artifacts in common: " + union2.countArtifacts());
            System.out.println("Total Artifacts remaining from root3 (variant STATIC): " + root1.countArtifacts());
            System.out.println("Total Artifacts remaining from rootbase (variant base static without features): " + rootbase.countArtifacts());
            //int total = union2.countArtifacts() + union.countArtifacts() + rootbase.countArtifacts();
            //System.out.println("Total Artifacts: " + total);

            union = Trees.slice(root1, root2);
            System.out.println("------------------");
            System.out.println("Total Artifacts in common: " + union.countArtifacts());
            System.out.println("Total Artifacts remaining from root1 (variant STATIC): " + root1.countArtifacts());
            System.out.println("Total Artifacts remaining from root2 (variant RUNTIME): " + root2.countArtifacts());
            //total = union.countArtifacts() + root1.countArtifacts() + root2.countArtifacts();
            //System.out.println("Total Artifacts: " + total);

            Node.Op union3 = Trees.slice(root2, union2);
            System.out.println("------------------");
            System.out.println("Total Artifacts in common runtime feature and base: " + union3.countArtifacts());
            System.out.println("Total Artifacts remaining from root1 (variant BASE): " + union2.countArtifacts());
            System.out.println("Total Artifacts remaining from root2 (variant RUNTIME): " + root2.countArtifacts());
        }

        service.close();
    }


    @Test(groups = {"integration", "java"})
    public void testCommit() {
        EccoService ecco = new EccoService();
        ecco.setRepositoryDir(Paths.get("C:\\Users\\gabil\\Desktop\\ECCO_Work\\testando-falsenegative\\Marlin\\variant_results\\repo"));
        ecco.open();

        ecco.setBaseDir(Paths.get("C:\\Users\\gabil\\Desktop\\ECCO_Work\\testando-falsenegative\\Marlin\\ecco\\HEATER_1_MAXTEMP.1,BASE.2"));
        //ecco.checkout("BASE.31");

        /*System.out.println("opened");
        ecco.setBaseDir(Paths.get("C:\\Users\\gabil\\Desktop\\test\\variant1"));
       */
        ecco.commit("HEATER_1_MAXTEMP.1,BASE.2");
        System.out.println("commited");
        //ecco.setBaseDir(Paths.get("C:\\Users\\gabil\\Desktop\\test\\variant2"));
        //ecco.commit("BASE.31");
        //System.out.println("commited");
        ecco.close();
    }

    @Test
    public void testCheckout() throws IOException {
        EccoService service = new EccoService();
        Path repofolder = Paths.get(resultMetrics_path);
        Path checkoutfolder = repofolder.resolve("checkout");
        service.setRepositoryDir(repofolder.resolve("repo"));
        service.open();
        List<String> runtimes = new ArrayList<>();
        String[] features = new String[]{"BASE", "ACTIVITYDIAGRAM", "COLLABORATIONDIAGRAM", "DEPLOYMENTDIAGRAM", "SEQUENCEDIAGRAM", "STATEDIAGRAM", "USECASEDIAGRAM", "LOGGING", "COGNITIVE"};//{"Base","New","Open","Save","ExitApp","Print","Cut","Copy","Paste","SelectAll","TimeDate","Undo","Redo","Find","FindNext","About","AboutMe","Fonts","LineWrap","LineNumber","Toolbar"};//{"STATES", "SOLVER", "GENERATOR", "UNDO", "EXTENDED", "BASE"};
        //checkout
        Long runtimeEccoCheckout, timeBefore, timeAfter;
        for (int i = 0; i < features.length; i++) {
            String config = features[i] + ".1";
            Path pathcheckout = Paths.get(checkoutfolder + File.separator + config);
            File checkoutfile = new File(String.valueOf(pathcheckout));
            checkoutfile.mkdir();
            service.setBaseDir(pathcheckout);
            timeBefore = System.currentTimeMillis();
            // depending on how the configs are, if lower case then uncomment the two lines below and comment the other following two lines
            //if(!config.contains("Base"))
            //  config += ", Base.1";
            if (!config.contains("BASE"))
                config += ", BASE.1";
            service.checkout(config);
            System.out.println(config + " checked out!");
            timeAfter = System.currentTimeMillis();
            runtimeEccoCheckout = timeAfter - timeBefore;
            runtimes.add("config: " + config + "  " + Long.toString(runtimeEccoCheckout));
        }
        //end checkout

        //close ecco repository
        service.close();

        Files.write(checkoutfolder.resolve("timeCheckoutIndividual.txt"), runtimes.stream().map(Object::toString).collect(Collectors.toList()));
    }

    //compares the ground truth variant with the variant retrieved by feature location
    @Test
    public void testCompareVariants() {
        File variantsrc = new File(resultMetrics_path, "ecco");
        File checkoutfile = new File(resultMetrics_path, "checkout");
        try {
            for (File path : variantsrc.listFiles()) {
                compareVariant(path, new File(checkoutfile + File.separator + path.getName()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //computes the metrics of variants this is not considering all the files match (just if the file exists) and to compute files metrics this is not considering all the lines match (just if the file exists)
    @Test
    public void getCSVInformation() throws IOException {
        File folder = new File(resultMetrics_path);
        File[] lista = folder.listFiles();
        Float matchesFiles = Float.valueOf(0), eccototalLines = Float.valueOf(0), originaltotalLines = Float.valueOf(0), missingFiles = Float.valueOf(0), remainingFiles = Float.valueOf(0), totalVariantsMatch = Float.valueOf(0), truepositiveLines = Float.valueOf(0), falsepositiveLines = Float.valueOf(0), falsenegativeLines = Float.valueOf(0);
        Boolean variantMatch = true;
        Float numberCSV = Float.valueOf(0);
        for (File file : lista) {
            if ((file.getName().indexOf(".csv") != -1) && !(file.getName().contains("features_report_each_project_commit")) && !(file.getName().contains("configurations"))) {
                Reader reader = Files.newBufferedReader(Paths.get(file.getAbsolutePath()));
                CSVReader csvReader = new CSVReaderBuilder(reader).build();
                List<String[]> matchesVariants = csvReader.readAll();

                for (int i = 1; i < matchesVariants.size(); i++) {
                    String[] line = matchesVariants.get(i);
                    truepositiveLines += Integer.valueOf(line[2]);
                    falsepositiveLines += Integer.valueOf(line[3]);
                    falsenegativeLines += Integer.valueOf(line[4]);
                    originaltotalLines += Integer.valueOf(line[5]);
                    eccototalLines += Integer.valueOf(line[6]);
                    if (line[1].equals("true")) {
                        variantMatch = true;
                        matchesFiles++;
                    } else if (line[1].equals("not")) {
                        variantMatch = false;
                        missingFiles++;
                    } else if (line[1].equals("justOnRetrieved")) {
                        variantMatch = false;
                        remainingFiles++;
                    } else {
                        variantMatch = false;
                        missingFiles++;
                    }
                }

                numberCSV++;
                if (variantMatch)
                    totalVariantsMatch++;
            }
        }

        Float totalVariantsNotMatch = (numberCSV - totalVariantsMatch);
        Float precisionVariants = totalVariantsMatch / (totalVariantsMatch + totalVariantsNotMatch);
        Float recallVariants = totalVariantsMatch / (numberCSV);
        Float f1scoreVariants = 2 * ((precisionVariants * recallVariants) / (precisionVariants + recallVariants));
        if (f1scoreVariants.toString().equals("NaN")) {
            f1scoreVariants = Float.valueOf(0);
        }
        Float precisionLines = Float.valueOf(truepositiveLines / (truepositiveLines + falsepositiveLines));
        Float recallLines = Float.valueOf(truepositiveLines / (truepositiveLines + falsenegativeLines));
        Float f1scorelines = 2 * ((precisionLines * recallLines) / (precisionLines + recallLines));
        Float precisionFiles = Float.valueOf(matchesFiles / (matchesFiles + remainingFiles));
        Float recallFiles = Float.valueOf(matchesFiles / (matchesFiles + missingFiles));
        Float f1scoreFiles = 2 * ((precisionFiles * recallFiles) / (precisionFiles + recallFiles));

        //write metrics in a csv file
        String filemetrics = "metrics.csv";
        //csv to report new features and features changed per git commit of the project
        try {
            FileWriter csvWriter = new FileWriter(resultMetrics_path + File.separator + filemetrics);
            List<List<String>> headerRows = Arrays.asList(
                    Arrays.asList("PrecisionVariant", "RecallVariant", "F1ScoreVariant", "PrecisionFiles", "RecallFiles", "F1ScoreFiles", "PrecisionLines", "RecalLines", "F1ScoreLines"),
                    Arrays.asList(precisionVariants.toString(), recallVariants.toString(), f1scoreVariants.toString(), precisionFiles.toString(), recallFiles.toString(), f1scoreFiles.toString(), precisionLines.toString(), recallLines.toString(), f1scorelines.toString())
            );
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

    //computes precision, recall and fscore per variant at line level
    @Test
    public void getCSVInformationPerVariant() throws IOException {
        File folder = new File(resultMetrics_path);
        File[] lista = folder.listFiles();

        for (File file : lista) {
            if ((file.getName().indexOf(".csv") != -1)) {
                Float matchesFiles = Float.valueOf(0), eccototalLines = Float.valueOf(0), originaltotalLines = Float.valueOf(0), missingFiles = Float.valueOf(0), remainingFiles = Float.valueOf(0), totalVariantsMatch = Float.valueOf(0), truepositiveLines = Float.valueOf(0), falsepositiveLines = Float.valueOf(0), falsenegativeLines = Float.valueOf(0);
                Reader reader = Files.newBufferedReader(Paths.get(file.getAbsolutePath()));
                CSVReader csvReader = new CSVReaderBuilder(reader).build();
                List<String[]> matchesVariants = csvReader.readAll();

                for (int i = 1; i < matchesVariants.size(); i++) {
                    String[] line = matchesVariants.get(i);
                    truepositiveLines += Integer.valueOf(line[2]);
                    falsepositiveLines += Integer.valueOf(line[3]);
                    falsenegativeLines += Integer.valueOf(line[4]);
                    originaltotalLines += Integer.valueOf(line[5]);
                    eccototalLines += Integer.valueOf(line[6]);
                    if (line[1].equals("true")) {
                        matchesFiles++;
                    } else if (line[1].equals("not")) {
                        missingFiles++;
                    } else if (line[1].equals("justOnRetrieved")) {
                        remainingFiles++;
                    } else {
                        missingFiles++;
                    }
                }

                Float precisionLines = Float.valueOf(truepositiveLines / (truepositiveLines + falsepositiveLines));
                Float recallLines = Float.valueOf(truepositiveLines / (truepositiveLines + falsenegativeLines));
                Float f1scorelines = 2 * ((precisionLines * recallLines) / (precisionLines + recallLines));
                Float precisionFiles = Float.valueOf(matchesFiles / (matchesFiles + remainingFiles));
                Float recallFiles = Float.valueOf(matchesFiles / (matchesFiles + missingFiles));
                Float f1scoreFiles = 2 * ((precisionFiles * recallFiles) / (precisionFiles + recallFiles));

                //write metrics in a csv file
                String filemetrics = "metrics_" + file.getName() + ".csv";
                //csv to report new features and features changed per git commit of the project
                try {
                    FileWriter csvWriter = new FileWriter(resultMetrics_path + File.separator + filemetrics);
                    List<List<String>> headerRows = Arrays.asList(
                            Arrays.asList("PrecisionFiles", "RecallFiles", "F1ScoreFiles", "PrecisionLines", "RecalLines", "F1ScoreLines"),
                            Arrays.asList(precisionFiles.toString(), recallFiles.toString(), f1scoreFiles.toString(), precisionLines.toString(), recallLines.toString(), f1scorelines.toString())
                    );
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
        }
    }


    private List<String> fileTypes = new LinkedList<String>();


    public void compareVariant(File srcOriginal, File srcEcco) throws IOException {
        fileTypes.add("java");

        LinkedList<File> filesVariant = new LinkedList<>();
        LinkedList<File> filesEcco = new LinkedList<>();
        getFilesToProcess(srcEcco, filesEcco);
        filesEcco.remove(srcEcco);
        getFilesToProcess(srcOriginal, filesVariant);
        filesVariant.remove(srcOriginal);
        String outputCSV = srcOriginal.getParentFile().getParentFile().getAbsolutePath();
        String fileStr = outputCSV + File.separator + srcOriginal.getName() + ".csv";
        File fWriter = new File(fileStr);
        FileWriter csvWriter = new FileWriter(fWriter);

        List<List<String>> headerRows = Arrays.asList(
                Arrays.asList("fileName", "matchFile", "truepositiveLines", "falsepositiveLines", "falsenegativeLines", "originaltotalLines", "retrievedtotalLines")
        );
        for (List<String> rowData : headerRows) {
            csvWriter.write(String.join(",", rowData));
            csvWriter.write("\n");
        }

        //files that are in ecco and variant
        for (File f : filesVariant) {
            Boolean fileExistsInEcco = false;
            Integer truepositiveLines = 0, falsepositiveLines = 0, falsenegativeLines = 0, originaltotalLines = 0, eccototalLines = 0;
            Boolean matchFiles = false;
            List<String> original = new ArrayList<>();
            List<String> revised = new ArrayList<>();

            String extension = f.getName().substring(f.getName().lastIndexOf('.') + 1);
            if (fileTypes.contains(extension) && !f.isDirectory()) {
                File filenew = new File(String.valueOf(f.toPath()));
                BufferedReader br = new BufferedReader(new FileReader(filenew.getAbsoluteFile()));
                String sCurrentLine;
                while ((sCurrentLine = br.readLine()) != null) {
                    sCurrentLine = sCurrentLine.trim().replaceAll("\t", "").replaceAll("\r", "").replaceAll(" ", "");
                    if (!sCurrentLine.equals("") && !sCurrentLine.startsWith("//") && !sCurrentLine.startsWith("/*") && !sCurrentLine.startsWith("*/") && !sCurrentLine.startsWith("*") && !sCurrentLine.startsWith("import")) {
                        original.add(sCurrentLine);
                    }
                }
                br.close();
                //compare text of files
                for (File fEcco : filesEcco) {
                    if (f.toPath().toString().substring(f.toPath().toString().indexOf("ecco\\") + 5).equals(fEcco.toPath().toString().substring(fEcco.toPath().toString().indexOf("checkout\\") + 9))) {
                        filenew = new File(String.valueOf(fEcco.toPath()));
                        br = new BufferedReader(new FileReader(filenew.getAbsoluteFile()));
                        while ((sCurrentLine = br.readLine()) != null) {
                            sCurrentLine = sCurrentLine.trim().replaceAll("\t", "").replaceAll("\r", "").replaceAll(" ", "");
                            if (!sCurrentLine.equals("") && !sCurrentLine.startsWith("//") && !sCurrentLine.startsWith("/*") && !sCurrentLine.startsWith("*/") && !sCurrentLine.startsWith("*") && !sCurrentLine.startsWith("import")) {
                                revised.add(sCurrentLine);
                            }
                        }
                        br.close();

                        // Compute diff. Get the Patch object. Patch is the container for computed deltas.
                        Patch<String> patch = null;
                        patch = DiffUtils.diff(original, revised);
                        ArrayList<String> insertedLines = new ArrayList<>();
                        ArrayList<String> changedLinesRevised = new ArrayList<>();
                        ArrayList<String> changedLinesOriginal = new ArrayList<>();
                        ArrayList<String> deletedLines = new ArrayList<>();
                        if (patch.getDeltas().size() == 0) {
                            //files match
                            matchFiles = true;
                        } else {
                            String del = "", insert = "";
                            for (Delta delta : patch.getDeltas()) {
                                String line = "";
                                if (delta.getType().toString().equals("INSERT")) {
                                    ArrayList<String> arraylines = (ArrayList<String>) delta.getRevised().getLines();
                                    for (String deltaaux : arraylines) {
                                        line = deltaaux.trim().replaceAll("\t", "").replaceAll(",", "").replaceAll(" ", "");
                                        if (!line.equals("") && !line.startsWith("//") && !line.startsWith("/*") && !line.startsWith("*/") && !line.startsWith("*")) {
                                            matchFiles = false;
                                            falsepositiveLines++;
                                            insert = line;
                                            insertedLines.add(insert);
                                        }
                                    }
                                } else if (delta.getType().toString().equals("CHANGE")) {
                                    ArrayList<String> arraylines = (ArrayList<String>) delta.getRevised().getLines();
                                    ArrayList<String> arrayOriginal = (ArrayList<String>) delta.getOriginal().getLines();
                                    for (String deltaaux : arraylines) {
                                        line = deltaaux.trim().replaceAll("\t", "").replaceAll(",", "").replaceAll(" ", "");
                                        if (!line.equals("") && !line.startsWith("//") && !line.startsWith("/*") && !line.startsWith("*/") && !line.startsWith("*")) {
                                            insert = line;
                                            changedLinesRevised.add(insert);
                                        }
                                    }
                                    for (String deltaaux : arrayOriginal) {
                                        line = deltaaux.trim().replaceAll("\t", "").replaceAll(",", "").replaceAll(" ", "");
                                        if (!line.equals("") && !line.startsWith("//") && !line.startsWith("/*") && !line.startsWith("*/") && !line.startsWith("*")) {
                                            insert = line;
                                            changedLinesOriginal.add(insert);
                                        }
                                    }
                                } else {
                                    ArrayList<String> arraylines = (ArrayList<String>) delta.getOriginal().getLines();
                                    for (String deltaaux : arraylines) {
                                        line = deltaaux.trim().replaceAll("\t", "").replaceAll(",", "").replaceAll(" ", "");
                                        if (!line.equals("") && !line.startsWith("//") && !line.startsWith("/*") && !line.startsWith("*/") && !line.startsWith("*")) {
                                            matchFiles = false;
                                            falsenegativeLines++;
                                            del = line;
                                            deletedLines.add(del);
                                        }
                                    }


                                }
                            }
                        }
                        String trimmingDiffLinesalingmentOr = "";
                        for (String changedLine : changedLinesOriginal) {
                            boolean found = false;
                            String aux = "";
                            for (String changedrevised : changedLinesRevised) {
                                if (changedrevised.contains("//#")) {
                                    aux = changedrevised.substring(0, changedrevised.indexOf("//#"));
                                    if (changedLine.equals(aux)) {
                                        found = true;
                                        aux = changedrevised;
                                        break;
                                    }
                                } else if (changedLine.equals(changedrevised)) {
                                    found = true;
                                    aux = changedrevised;
                                    break;
                                } else {
                                    if (changedrevised.contains(changedLine)) {
                                        trimmingDiffLinesalingmentOr += changedLine;
                                        if (falsenegativeLines > 0)
                                            falsenegativeLines--;
                                    }
                                }

                            }
                            if (!found)
                                falsenegativeLines++;
                            else
                                changedLinesRevised.remove(aux);
                        }
                        ArrayList<String> changedLinesRevisedAux = new ArrayList<>();
                        changedLinesRevisedAux.addAll(changedLinesRevised);
                        for (String changedrevised : changedLinesRevisedAux) {
                            if (trimmingDiffLinesalingmentOr.contains(changedrevised))
                                changedLinesRevised.remove(changedrevised);
                        }

                        if (changedLinesRevised.size() > 0) {
                            falsepositiveLines += changedLinesRevised.size();
                            for (String changedline : changedLinesRevised) {
                                insertedLines.add(changedline);
                            }
                        }


                        ArrayList<String> diffDeleted = new ArrayList<>();
                        Boolean found = false;
                        for (String line : deletedLines) {
                            for (String insertLine : insertedLines) {
                                if (insertLine.equals(line) || insertLine.contains(line)) {
                                    if (falsepositiveLines > 0)
                                        falsepositiveLines--;
                                    if (falsenegativeLines > 0)
                                        falsenegativeLines--;
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                diffDeleted.add(line);
                            } else {
                                insertedLines.remove(line);
                                found = false;
                            }
                        }

                        for (String line : changedLinesOriginal) {
                            for (String insertedLine : insertedLines) {
                                if (insertedLine.equals(line)) {
                                    if (falsepositiveLines > 0)
                                        falsepositiveLines--;
                                    if (falsenegativeLines > 0)
                                        falsenegativeLines--;
                                    found = true;
                                    break;
                                }
                            }
                            if (found) {
                                insertedLines.remove(line);
                                found = false;
                            }
                        }

                        if (falsepositiveLines == 0 && falsenegativeLines == 0)
                            matchFiles = true;
                        else {
                            if (diffDeleted.size() > 0) {
                                for (String line : diffDeleted) {
                                    System.out.println("file: " + fEcco.getAbsolutePath() + " TYPE: DELETE delta: " + line);
                                }
                            }
                            if (insertedLines.size() > 0) {
                                for (String line : insertedLines) {
                                    System.out.println("file: " + fEcco.getAbsolutePath() + " TYPE: INSERT delta: " + line);
                                }
                            }
                        }

                        eccototalLines = (revised.size() - 1);
                        originaltotalLines = original.size() - 1;
                        truepositiveLines = eccototalLines - (falsepositiveLines);

                        List<List<String>> resultRows = Arrays.asList(
                                Arrays.asList(f.toPath().toString().substring(f.toPath().toString().indexOf("ecco\\") + 5).replace(",", "and"), matchFiles.toString(), truepositiveLines.toString(), falsepositiveLines.toString(), falsenegativeLines.toString(), originaltotalLines.toString(), eccototalLines.toString())
                        );
                        for (List<String> rowData : resultRows) {
                            csvWriter.append(String.join(",", rowData));
                            csvWriter.append("\n");
                        }
                        fileExistsInEcco = true;
                    }

                }
                if (!fileExistsInEcco) {
                    for (String line : original) {
                        String lineaux = line.trim().replaceAll("\t", "").replaceAll(",", "").replaceAll(" ", "");
                        if (!lineaux.equals("") && !lineaux.startsWith("//") && !lineaux.startsWith("/*") && !lineaux.startsWith("*/") && !lineaux.startsWith("*")) {
                            originaltotalLines++;
                        }
                    }
                    List<List<String>> resultRows = Arrays.asList(
                            Arrays.asList(f.toPath().toString().substring(f.toPath().toString().indexOf("ecco\\") + 5).replace(",", "and"), "not", "0", "0", originaltotalLines.toString(), originaltotalLines.toString(), eccototalLines.toString())
                    );
                    for (List<String> rowData : resultRows) {
                        csvWriter.append(String.join(",", rowData));
                        csvWriter.append("\n");
                    }
                }
            }

        }


        //files that are just in ecco and not in variant
        for (File fEcco : filesEcco) {
            Integer truepositiveLines = 0, falsepositiveLines = 0, falsenegativeLines = 0, originaltotalLines = 0, eccototalLines = 0;
            Boolean matchFiles = false;

            String extension = fEcco.getName().substring(fEcco.getName().lastIndexOf('.') + 1);
            if (fileTypes.contains(extension) && !fEcco.isDirectory()) {

                Boolean existJustEcco = true;
                for (File f : filesVariant) {
                    if (fEcco.toPath().toString().substring(fEcco.toPath().toString().indexOf("checkout\\") + 9).equals(f.toPath().toString().substring(f.toPath().toString().indexOf("ecco\\") + 5))) {
                        existJustEcco = false;
                    }
                }
                //file just exist in ecco
                if (existJustEcco) {
                    //compare text of files
                    List<String> original = new ArrayList<>();//Files.readAllLines(fEcco.toPath());
                    File filenew = new File(String.valueOf(fEcco.toPath()));
                    BufferedReader br = new BufferedReader(new FileReader(filenew.getAbsoluteFile()));
                    String sCurrentLine;
                    while ((sCurrentLine = br.readLine()) != null) {
                        sCurrentLine = sCurrentLine.trim().replaceAll("\t", "").replaceAll("\r", "").replaceAll(" ", "");
                        if (!sCurrentLine.equals("") && !sCurrentLine.startsWith("//") && !sCurrentLine.startsWith("/*") && !sCurrentLine.startsWith("*/") && !sCurrentLine.startsWith("*") && !sCurrentLine.startsWith("import")) {
                            original.add(sCurrentLine);
                        }
                    }
                    br.close();
                    matchFiles = false;
                    eccototalLines = original.size() - 1;
                    falsepositiveLines = eccototalLines;
                    falsenegativeLines = 0;
                    originaltotalLines = 0;
                    truepositiveLines = eccototalLines - (falsepositiveLines);

                    List<List<String>> resultRows = Arrays.asList(
                            Arrays.asList(fEcco.toPath().toString().substring(fEcco.toPath().toString().indexOf("checkout\\") + 9).replace(",", "and"), "justOnRetrieved", truepositiveLines.toString(), falsepositiveLines.toString(), falsenegativeLines.toString(), originaltotalLines.toString(), eccototalLines.toString())
                    );
                    for (List<String> rowData : resultRows) {
                        csvWriter.append(String.join(",", rowData));
                        csvWriter.append("\n");
                    }
                }
            }
        }
        csvWriter.close();
    }


    private void getFilesToProcess(File f, List<File> files) {
        if (f.isDirectory()) {
            for (File file : f.listFiles()) {
                if (!files.contains(f) && !file.getName().equals(f.getName()))
                    files.add(f);
                getFilesToProcess(file, files);
            }
        } else if (f.isFile()) {
            if (!f.getName().equals(".config") && !f.getName().equals(".hashes") && !f.getName().equals(".warnings"))
                files.add(f);
        }
    }


}
