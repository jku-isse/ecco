package at.jku.isse.ecco.service.test;

import java.io.*;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import at.jku.isse.ecco.adapter.cpp.data.*;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

import javax.swing.text.StyledEditorKit;

import static at.jku.isse.ecco.util.Trees.computeDepth;
import static at.jku.isse.ecco.util.Trees.slice;

public class FeatureRevisionLocationTest {
    //directory where you have the folder with the artifacts of the target systyem
    public final String resultsCSVs_path = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\RunningExample";
    //directory with the folder "variant_results" inside the folder with the artifacts of the target systyem
    public final String resultMetrics_path = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\testadapter\\variant_results";
    //directory with the file "configurations.csv" inside the folder with the artifacts of the target systyem
    public final String configuration_path = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\RunningExample\\configurations.csv";
    public final String csvcomparison_path = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\RunningExample\\ResultsCompareVariants";
    //directory where you have the folder with the artifacts of Marlin target systyem
    public final String marlinFolder = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\CaseStudies\\Test500commits\\Marlin\\ResultsCompareVariantsRandom";
    //directory where you have the folder with the artifacts of LibSSH target systyem
    public final String libsshFolder = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\CaseStudies\\Test500commits\\LibSSH\\ResultsCompareVariantsRandom";
    //directory where you have the folder with the artifacts of SQLite target systyem
    public final String sqliteFolder = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\CaseStudies\\Test500commits\\SQLite\\ResultsCompareVariantsRandom";
    //directory where you have the folder with the artifacts of Bison target systyem
    public final String bisonFolder = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\CaseStudies\\Test500commits\\Bison\\ResultsCompareVariantsRandom";
    //directory where you have the folder with the artifacts of Irssi target systyem
    public final String irssiFolder = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\CaseStudies\\Test500commits\\Irssi\\ResultsCompareVariantsRandom";
    //directory where you have the folder with the artifacts of Curl target systyem
    public final String curlFolder = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\CaseStudies\\Test500commits\\Curl\\ResultsCompareVariantsRandom";
    //directory where you want to store the result file containing the metrics computed for all target systems
    public final String metricsResultFolder = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\RunningExample";
    private List<String> fileTypes = new LinkedList<String>();


    //feature revision location where the traces are computed by the input containing a set of feature revisions (configuration) and its artifacts (variant source code)
    @org.testng.annotations.Test
    public void TestEccoCommit() throws IOException {
        ArrayList<String> configsToCommit = new ArrayList<>();
        File configuration = new File(configuration_path);
        Path OUTPUT_DIR = Paths.get(resultMetrics_path);
        File eccoFolder = new File(resultsCSVs_path + File.separator + "Input_variants" + File.separator);
        BufferedReader csvReader = null;
        try {
            csvReader = new BufferedReader(new FileReader(configuration));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        String row = null;
        while ((row = csvReader.readLine()) != null) {
            if (!(row.equals("")) && !(row.contains("CommitNumber"))) {
                String[] data = row.split(",");
                String conf = "";
                for (int i = 2; i < data.length; i++) {
                    if (i < data.length - 1)
                        conf += data[i] + ",";
                    else
                        conf += data[i];
                }
                configsToCommit.add(conf);
            }
        }

        csvReader.close();

        eccoCommit(eccoFolder, OUTPUT_DIR, configsToCommit);
    }


    //checkout each set of feature revisions (configuration) and its artifacts (variant source code) by the traces located before by the feature revision location (TestEccoCommit)
    @org.testng.annotations.Test
    public void TestEccoCheckout() throws IOException {
        ArrayList<String> configsToCheckout = new ArrayList<>();
        File configuration = new File(configuration_path);
        Path OUTPUT_DIR = Paths.get(resultMetrics_path + File.separator);
        //File eccoFolder = new File(resultsCSVs_path+File.separator+"Input_variants"+File.separator);
        //File checkoutFolder = new File(resultMetrics_path+File.separator+"checkout"+File.separator);
        BufferedReader csvReader = null;
        try {
            csvReader = new BufferedReader(new FileReader(configuration));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        String row = null;
        while ((row = csvReader.readLine()) != null) {
            if (!(row.equals("")) && !(row.contains("CommitNumber"))) {
                String[] data = row.split(",");
                String conf = "";
                for (int i = 2; i < data.length; i++) {
                    if (i < data.length - 1)
                        conf += data[i] + ",";
                    else
                        conf += data[i];
                }
                configsToCheckout.add(conf);
            }
        }

        csvReader.close();
        eccoCheckout(configsToCheckout, OUTPUT_DIR);
    }


    //compare the ground truth variants with the composed variants (containing the artifacts mapped according to the feature revision location)
    @org.testng.annotations.Test
    public void TestCompareVariants() {
        //"input_variants" folder contains the ground truth variants and "checkout" folder contains the composed variants
        File variantsrc = new File(resultsCSVs_path, "ecco");
        File checkoutfile = new File(resultMetrics_path, "checkout");
        try {
            for (File path : checkoutfile.listFiles()) {
                compareVariant(new File(variantsrc + File.separator + path.getName()), path);
                //for (File path : variantsrc.listFiles()) {
                //    compareVariant(path, new File(checkoutfile + File.separator + path.getName()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @org.testng.annotations.Test
    public void TestWarnings() throws IOException {
        File checkoutfile = new File(resultMetrics_path, "checkout");
        for (File path : checkoutfile.listFiles()) {
            String pathName = path.getName();
            File pathCompareVariants = new File(checkoutfile.getParentFile().getParentFile(), "ResultsCompareVariants\\" + pathName + ".csv");
            File inputVariant = new File(checkoutfile.getParentFile().getParentFile(), "Input_variants\\" + pathName);
            Map<String, String> filenames = new HashMap<>();
            BufferedReader csvReader = new BufferedReader(new FileReader(pathCompareVariants));
            String row = "";
            Boolean enter = false;
            while ((row = csvReader.readLine()) != null) {
                if (enter) {
                    String[] data = row.split(",");
                    // do something with the data
                    if (!data[1].toUpperCase().equals("TRUE")) {
                        filenames.put(data[0].substring(data[0].lastIndexOf("\\") + 1), data[0].substring(data[0].indexOf("\\")));
                    }
                } else {
                    enter = true;
                }
            }
            csvReader.close();
            File[] files = path.listFiles((d, name) -> name.endsWith(".warnings"));
            File warningsFile = files[0];
            FileReader fr = new FileReader(warningsFile);   //reads the file
            BufferedReader br = new BufferedReader(fr);  //creates a buffering character input stream
            StringBuffer sb = new StringBuffer();    //constructs a string buffer with no characters
            String line;
            String[] feats = path.getName().split(",");
            ArrayList<String> features = new ArrayList<>();
            ArrayList<String> featureswsurplus = new ArrayList<>();
            ArrayList<String> featureswmissing = new ArrayList<>();
            ArrayList<String> associations = new ArrayList<>();
            for (String f : feats) {
                features.add(f);
            }
            int countsurplus = 0;
            int countmissing = 0;
            while ((line = br.readLine()) != null) {
                sb.append(line);      //appends line to string buffer
                sb.append("\n");     //line feed
                if (line.contains("SURPLUS")) {
                    if (!line.contains(",")) {
                        String aux = line.substring(line.indexOf("(") + 1, line.indexOf(")"));
                        if (!featureswsurplus.contains(aux))
                            featureswsurplus.add(aux.trim());
                    } else {
                        String[] featsw = line.split(",");
                        for (String f : featsw) {
                            String aux = f;
                            if (f.contains("("))
                                aux = f.substring(f.indexOf("(") + 1);
                            else if (f.contains(")"))
                                aux = f.substring(0, f.indexOf(")"));
                            if (!featureswsurplus.contains(aux.trim()))
                                featureswsurplus.add(aux.trim());
                        }
                    }
                    String association = line.substring(line.indexOf("id: ") + 4);
                    if (!associations.contains(association))
                        associations.add(association);
                } else if (line.contains("MISSING")) {
                    if (!line.contains(",")) {
                        String aux = line.substring(line.indexOf("(") + 1, line.indexOf(")"));
                        if (!featureswmissing.contains(aux))
                            featureswmissing.add(aux.trim());
                    } else {
                        String[] featsw = line.split(",");
                        for (String f : featsw) {
                            String aux = f;
                            if (f.contains("("))
                                aux = f.substring(f.indexOf("(") + 1);
                            else if (f.contains(")"))
                                aux = f.substring(0, f.indexOf(")"));
                            if (!featureswmissing.contains(aux.trim()))
                                featureswmissing.add(aux.trim());
                        }
                    }
                }
            }
            for (String f : featureswsurplus) {
                if (!features.contains(f)) {
                    countsurplus++;
                }
            }
            for (String f : featureswmissing) {
                if (!features.contains(f)) {
                    countmissing++;
                }
            }
            fr.close();    //closes the stream and release the resources
            //System.out.println("\nContents of File: " + path.getName());
            //System.out.println(sb.toString());   //returns a string that textually represents the object
            System.out.println("Number of features surplus: " + countsurplus + " Number of features missing: " + countmissing);

            if (associations.size() > 0) {
                EccoService service = new EccoService();
                Path repo = Paths.get(resultMetrics_path);
                service.setRepositoryDir(repo.resolve("repo"));
                service.open();
                Collection<? extends Association> assocrepo = service.getRepository().getAssociations();
                for (Association assoc : assocrepo) {
                    if (associations.contains(assoc.getId()) && filenames.size() > 0) {
                        ArrayList<String> lines = new ArrayList<>();
                        ArrayList<String> linesInputVariant = new ArrayList<>();
                        ArrayList<String> filenamesAssociation = new ArrayList<>();
                        ArrayList<String> linesSurplus = new ArrayList<>();
                        System.out.println("Artifacts: " + assoc.getRootNode().countArtifacts() + " " + assoc.getId());
                        computeString((Node.Op) assoc.getRootNode(), filenames, lines, filenamesAssociation);
                        for (String fa : filenamesAssociation) {
                            if (filenames.get(fa) != null) {
                                fr = new FileReader(inputVariant + filenames.get(fa));   //reads the file
                                br = new BufferedReader(fr);  //creates a buffering character input stream
                                while ((row = br.readLine()) != null) {
                                    linesInputVariant.add(row);
                                }
                                br.close();
                                for (String l : lines) {
                                    if (!linesInputVariant.contains(l.replaceAll("\n", ""))) {
                                        linesSurplus.add(l);
                                    }
                                }
                            }
                            else{
                                System.out.println("NULL");
                            }
                        }
                        for (String lsurplus : linesSurplus) {
                            System.out.println("Lines Surplus: " + lsurplus);
                        }
                    }else{
                        System.out.println("FILE MATCH");
                    }
                }
            }

        }
    }


    private void computeString(Node.Op node, Map<String, String> filenames, ArrayList<String> lines, ArrayList<String> filenamesAssociation) {
        if (node.getArtifact() != null && node.getArtifact().getData() != null) {
            if (node.getArtifact().getData() instanceof PluginArtifactData) {
                System.out.println("SOURCE FILE: " + ((PluginArtifactData) node.getArtifact().getData()).getPath());
                filenamesAssociation.add(((PluginArtifactData) node.getArtifact().getData()).getPath().toString());
                if (filenames.containsKey(((PluginArtifactData) node.getArtifact().getData()).getPath().toString())) {
                    System.out.println("ENTROU " + node.getArtifact().getData());
                    for (Node.Op childNode : node.getChildren()) {
                        System.out.println(childNode.getArtifact().getData());
                        visitingNodes(childNode, lines);
                    }
                }
            }
        }
        for (Node.Op childNode : node.getChildren()) {
            computeString(childNode, filenames, lines, filenamesAssociation);
        }
    }

    private void visitingNodes(Node childNode, ArrayList<String> lines) {
        if (childNode.getArtifact().toString().equals("INCLUDES") || childNode.getArtifact().toString().equals("FUNCTIONS")) {
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, lines);
                }
            }
        } else if (childNode.getArtifact().toString().equals("FIELDS")) {
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    //fields[0] += node.getArtifact().getData() + "\n";
                    lines.add(node.getArtifact().getData().toString());
                }
            }
        } else if (childNode.getArtifact().toString().equals("DEFINES")) {
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    lines.add(node.getArtifact().getData().toString());
                    // defines[0] += node.getArtifact().getData() + "\n";
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof IncludeArtifactData)) {
            final IncludeArtifactData artifactData = (IncludeArtifactData) childNode.getArtifact().getData();
            lines.add(artifactData.toString());
            //includes[0] += artifactData.toString() + "\n";
        } else if ((childNode.getArtifact().getData() instanceof LineArtifactData)) {
            // code[0] += ((LineArtifactData) childNode.getArtifact().getData()).getLine() + "\n";
            lines.add(((LineArtifactData) childNode.getArtifact().getData()).getLine());
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, lines);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof FunctionArtifactData)) {
            //code[0] += "\n" + ((FunctionArtifactData) childNode.getArtifact().getData()).getSignature() + "\n";//artifactData.toString() + "{\n";
            lines.add(((FunctionArtifactData) childNode.getArtifact().getData()).getSignature());
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, lines);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof BlockArtifactData)) {
            // code[0] += ((BlockArtifactData) childNode.getArtifact().getData()).getBlock()+ "\n";
            lines.add(((BlockArtifactData) childNode.getArtifact().getData()).getBlock());
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, lines);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof DoBlockArtifactData)) {
            //code[0] += ((DoBlockArtifactData) childNode.getArtifact().getData()).getDoBlock()+ "\n";
            lines.add(((DoBlockArtifactData) childNode.getArtifact().getData()).getDoBlock());
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, lines);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof ForBlockArtifactData)) {
            //code[0] += ((ForBlockArtifactData) childNode.getArtifact().getData()).getForBlock()+ "\n";
            lines.add(((ForBlockArtifactData) childNode.getArtifact().getData()).getForBlock());
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, lines);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof IfBlockArtifactData)) {
            //code[0] += ((IfBlockArtifactData) childNode.getArtifact().getData()).getIfBlock()+ "\n";
            lines.add(((IfBlockArtifactData) childNode.getArtifact().getData()).getIfBlock());
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, lines);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof ProblemBlockArtifactData)) {
            //code[0] += ((ProblemBlockArtifactData) childNode.getArtifact().getData()).getProblemBlock()+ "\n";
            lines.add(((ProblemBlockArtifactData) childNode.getArtifact().getData()).getProblemBlock());
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, lines);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof SwitchBlockArtifactData)) {
            //code[0] += ((SwitchBlockArtifactData) childNode.getArtifact().getData()).getSwitchBlock()+ "\n";
            lines.add(((SwitchBlockArtifactData) childNode.getArtifact().getData()).getSwitchBlock());
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, lines);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof WhileBlockArtifactData)) {
            //code[0] += ((WhileBlockArtifactData) childNode.getArtifact().getData()).getWhileBlock()+ "\n";
            lines.add(((WhileBlockArtifactData) childNode.getArtifact().getData()).getWhileBlock());
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node, lines);
                }
            }
        } else if (childNode.getArtifact().getData() instanceof CaseBlockArtifactData) {
            if (((CaseBlockArtifactData) childNode.getArtifact().getData()).getSameline()) {
                //code[0] += ((CaseBlockArtifactData) childNode.getArtifact().getData()).getCaseblock();
                lines.add(((CaseBlockArtifactData) childNode.getArtifact().getData()).getCaseblock());
                if (childNode.getChildren().size() > 0) {
                    for (Node node : childNode.getChildren()) {
                        lines.add(node.getArtifact().getData().toString());
                        //code[0] += node.getArtifact().getData();
                    }
                }
                // code[0] += "\n";
            } else {
                lines.add(((CaseBlockArtifactData) childNode.getArtifact().getData()).getCaseblock());
                // code[0] += ((CaseBlockArtifactData) childNode.getArtifact().getData()).getCaseblock()+ "\n";
                if (childNode.getChildren().size() > 0) {
                    for (Node node : childNode.getChildren()) {
                        visitingNodes(node, lines);
                    }
                }
            }

        }
    }

    //get the metrics of each and for all the target projects together.
    //To compute the metrics of variants this is considering all the files match and to compute files metrics this is considering all the lines match
    @org.testng.annotations.Test
    public void GetCSVInformationTotalTest() throws IOException {
        //set into this list of File the folders with csv files resulted from the comparison of variants of each target project
        File[] folder = {//new File(csvcomparison_path)
                new File(marlinFolder),
                new File(libsshFolder),
                new File(sqliteFolder),
                new File(bisonFolder),
                new File(irssiFolder),
                new File(curlFolder)};
        //write metrics in a csv file
        String filemetrics = "RandomMetricsEachAndTogether-no-inserted-lines.csv";
        FileWriter csvWriter = new FileWriter(metricsResultFolder + File.separator + filemetrics);

        Float totalmeanRunEccoCommit = Float.valueOf(0), totalmeanRunEccoCheckout = Float.valueOf(0), totalmeanRunPPCheckoutCleanVersion = Float.valueOf(0), totalmeanRunPPCheckoutGenerateVariant = Float.valueOf(0), totalmeanRunGitCommit = Float.valueOf(0), totalmeanRunGitCheckout = Float.valueOf(0);
        Float totaltotalnumberFiles = Float.valueOf(0), totalmatchesFiles = Float.valueOf(0), totaleccototalLines = Float.valueOf(0), totaloriginaltotalLines = Float.valueOf(0), totalmissingFiles = Float.valueOf(0), totalremainingFiles = Float.valueOf(0), totaltotalVariantsMatch = Float.valueOf(0), totaltruepositiveLines = Float.valueOf(0), totalfalsepositiveLines = Float.valueOf(0), totalfalsenegativeLines = Float.valueOf(0),
                totaltruepositiveLinesEachFile = Float.valueOf(0), totalfalsepositiveLinesEachFile = Float.valueOf(0), totalfalsenegativeLinesEachFile = Float.valueOf(0), totalnumberTotalFilesEachVariant = Float.valueOf(0), totalmatchFilesEachVariant = Float.valueOf(0), totaleccototalLinesEachFile = Float.valueOf(0), totaloriginaltotalLinesEachFile = Float.valueOf(0), totalnumberCSV = Float.valueOf(0);
        for (int j = 0; j < folder.length; j++) {
            File[] lista = folder[j].listFiles();
            Float meanRunEccoCommit = Float.valueOf(0), meanRunEccoCheckout = Float.valueOf(0), meanRunPPCheckoutCleanVersion = Float.valueOf(0), meanRunPPCheckoutGenerateVariant = Float.valueOf(0), meanRunGitCommit = Float.valueOf(0), meanRunGitCheckout = Float.valueOf(0);
            Float totalnumberFiles = Float.valueOf(0), matchesFiles = Float.valueOf(0), eccototalLines = Float.valueOf(0), originaltotalLines = Float.valueOf(0), missingFiles = Float.valueOf(0), remainingFiles = Float.valueOf(0), totalVariantsMatch = Float.valueOf(0), truepositiveLines = Float.valueOf(0), falsepositiveLines = Float.valueOf(0), falsenegativeLines = Float.valueOf(0),
                    truepositiveLinesEachFile = Float.valueOf(0), falsepositiveLinesEachFile = Float.valueOf(0), falsenegativeLinesEachFile = Float.valueOf(0), numberTotalFilesEachVariant = Float.valueOf(0), matchFilesEachVariant = Float.valueOf(0), eccototalLinesEachFile = Float.valueOf(0), originaltotalLinesEachFile = Float.valueOf(0);
            Boolean variantMatch = true;
            Float numberCSV = Float.valueOf(0);
            for (File file : lista) {
                if ((file.getName().indexOf(".csv") != -1) && !(file.getName().contains("features_report_each_project_commit")) && !(file.getName().contains("configurations"))) {
                    Reader reader = Files.newBufferedReader(Paths.get(file.getAbsolutePath()));
                    CSVReader csvReader = new CSVReaderBuilder(reader).build();
                    List<String[]> matchesVariants = csvReader.readAll();

                    if (file.getName().contains("runtime")) {
                        for (int i = 0; i < matchesVariants.size(); i++) {
                            if (i != 0) {
                                String[] runtimes = matchesVariants.get(i);
                                meanRunEccoCommit += Float.valueOf(runtimes[2]);
                            }
                        }
                        totalmeanRunEccoCommit += meanRunEccoCommit;
                        totalmeanRunEccoCheckout += meanRunEccoCheckout;
                        totalmeanRunGitCommit += meanRunGitCommit;
                        totalmeanRunGitCheckout += meanRunGitCheckout;
                        totalmeanRunPPCheckoutCleanVersion += meanRunPPCheckoutCleanVersion;
                        totalmeanRunPPCheckoutGenerateVariant += meanRunPPCheckoutGenerateVariant;
                    } else {
                        for (int i = 1; i < matchesVariants.size(); i++) {

                            String[] line = matchesVariants.get(i);
                            truepositiveLines += Integer.valueOf(line[2]);
                            falsepositiveLines += Integer.valueOf(line[3]);
                            falsenegativeLines += Integer.valueOf(line[4]);
                            truepositiveLinesEachFile = Float.valueOf(Integer.valueOf(line[2]));
                            falsepositiveLinesEachFile = Float.valueOf(Integer.valueOf(line[3]));
                            falsenegativeLinesEachFile = Float.valueOf(Integer.valueOf(line[4]));
                            originaltotalLines += Integer.valueOf(line[5]);
                            eccototalLines += Integer.valueOf(line[6]);
                            originaltotalLinesEachFile = Float.valueOf(Integer.valueOf(line[5]));
                            eccototalLinesEachFile = Float.valueOf(Integer.valueOf(line[6]));

                            totaltruepositiveLines += truepositiveLines;
                            totalfalsepositiveLines += falsepositiveLines;
                            totalfalsenegativeLines += falsenegativeLines;
                            totaltruepositiveLinesEachFile += totaltruepositiveLinesEachFile;
                            totalfalsepositiveLinesEachFile += falsepositiveLinesEachFile;
                            totalfalsenegativeLinesEachFile += falsenegativeLinesEachFile;
                            totaloriginaltotalLines += originaltotalLines;
                            totaleccototalLines += eccototalLines;
                            totaloriginaltotalLinesEachFile += originaltotalLinesEachFile;
                            totaleccototalLinesEachFile += eccototalLinesEachFile;

                            if (line[1].toUpperCase().equals("TRUE")) {

                                matchFilesEachVariant++;
                                totalmatchFilesEachVariant++;
                                matchesFiles++;
                                totalmatchesFiles++;
                                numberTotalFilesEachVariant += 1;
                                totalnumberTotalFilesEachVariant++;
                            } else if (line[1].equals("not")) {
                                variantMatch = false;
                                missingFiles++;
                                totalmissingFiles++;
                                numberTotalFilesEachVariant += 1;
                                totalnumberTotalFilesEachVariant++;
                            } else if (line[1].equals("justOnRetrieved")) {
                                variantMatch = false;
                                remainingFiles++;
                                totalremainingFiles++;
                            } else {
                                variantMatch = false;
                                missingFiles++;
                                totalmissingFiles++;
                            }
                        }
                    }
                    numberCSV++;
                    totalnumberCSV++;
                    if (variantMatch && Float.compare(matchFilesEachVariant, numberTotalFilesEachVariant) == 0) {
                        totalVariantsMatch++;
                        totaltotalVariantsMatch++;
                    }
                }
                numberTotalFilesEachVariant = Float.valueOf(0);
                matchFilesEachVariant = Float.valueOf(0);
                variantMatch = true;
            }
            meanRunEccoCommit = (meanRunEccoCommit / numberCSV) / 1000;
            meanRunEccoCheckout = (meanRunEccoCheckout / numberCSV) / 1000;
            meanRunGitCommit = (meanRunGitCommit / numberCSV) / 1000;
            meanRunGitCheckout = (meanRunGitCheckout / numberCSV) / 1000;
            Float totalVariantsNotMatch = (numberCSV - totalVariantsMatch);
            Float precisionVariants = totalVariantsMatch / (totalVariantsMatch + totalVariantsNotMatch);
            Float recallVariants = totalVariantsMatch / (numberCSV);
            Float f1scoreVariants = 2 * ((precisionVariants * recallVariants) / (precisionVariants + recallVariants));
            Float precisionLines = Float.valueOf(truepositiveLines / (truepositiveLines + falsepositiveLines));
            Float recallLines = Float.valueOf(truepositiveLines / (truepositiveLines + falsenegativeLines));
            Float f1scorelines = 2 * ((precisionLines * recallLines) / (precisionLines + recallLines));
            Float precisionFiles = Float.valueOf(matchesFiles / (matchesFiles + remainingFiles));
            Float recallFiles = Float.valueOf(matchesFiles / (matchesFiles + missingFiles));
            Float f1scoreFiles = 2 * ((precisionFiles * recallFiles) / (precisionFiles + recallFiles));


            //csv to report new features and features changed per git commit of the project
            try {
                List<List<String>> headerRows = Arrays.asList(Arrays.asList(folder[j].getName()),
                        Arrays.asList("PrecisionFiles", "RecallFiles", "F1ScoreFiles", "PrecisionLines", "RecalLines", "F1ScoreLines"),
                        Arrays.asList(precisionFiles.toString(), recallFiles.toString(), f1scoreFiles.toString(), precisionLines.toString(), recallLines.toString(), f1scorelines.toString()),
                        Arrays.asList("MeanRuntime"),
                        Arrays.asList(meanRunEccoCommit.toString(), meanRunEccoCheckout.toString(), meanRunGitCommit.toString(), meanRunGitCheckout.toString())
                );
                for (List<String> rowData : headerRows) {
                    csvWriter.append(String.join(",", rowData));
                    csvWriter.append("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        totalmeanRunEccoCommit = (totalmeanRunEccoCommit / totalnumberCSV) / 1000;
        Float totalVariantsNotMatch = (totalnumberCSV - totaltotalVariantsMatch);
        Float precisionVariants = totaltotalVariantsMatch / (totaltotalVariantsMatch + totalVariantsNotMatch);
        Float recallVariants = totaltotalVariantsMatch / (totalnumberCSV);
        Float precisionLines = Float.valueOf(totaltruepositiveLines / (totaltruepositiveLines + totalfalsepositiveLines));
        Float recallLines = Float.valueOf(totaltruepositiveLines / (totaltruepositiveLines + totalfalsenegativeLines));
        Float f1scorelines = 2 * ((precisionLines * recallLines) / (precisionLines + recallLines));
        Float precisionFiles = Float.valueOf(totalmatchesFiles / (totalmatchesFiles + totalremainingFiles));
        Float recallFiles = Float.valueOf(totalmatchesFiles / (totalmatchesFiles + totalmissingFiles));
        Float f1scoreFiles = 2 * ((precisionFiles * recallFiles) / (precisionFiles + recallFiles));
        List<List<String>> totalRows = Arrays.asList(Arrays.asList("Three projects together"),
                Arrays.asList("TotalPrecisionFiles", "TotalRecallFiles", "TotalF1ScoreFiles", "TotalPrecisionLines", "TotalRecalLines", "TotalF1ScoreLines"),
                Arrays.asList(precisionFiles.toString(), recallFiles.toString(), f1scoreFiles.toString(), precisionLines.toString(), recallLines.toString(), f1scorelines.toString()),
                Arrays.asList("MeanRuntime"),
                Arrays.asList(totalmeanRunEccoCommit.toString())
        );
        for (List<String> rowData : totalRows) {
            csvWriter.append(String.join(",", rowData));
            csvWriter.append("\n");
        }
        csvWriter.flush();
        csvWriter.close();
    }


    public void compareVariant(File srcOriginal, File srcEcco) throws IOException {
        fileTypes.add("c");
        fileTypes.add("cpp");
        fileTypes.add("h");
        fileTypes.add("hpp");
        fileTypes.add("txt");
        fileTypes.add("md");
        fileTypes.add("xml");
        fileTypes.add("html");
        fileTypes.add("css");
        fileTypes.add("js");
        fileTypes.add("java");

        LinkedList<File> filesVariant = new LinkedList<>();
        LinkedList<File> filesEcco = new LinkedList<>();
        getFilesToProcess(srcEcco, filesEcco);
        filesEcco.remove(srcEcco);
        getFilesToProcess(srcOriginal, filesVariant);
        filesVariant.remove(srcOriginal);
        String outputCSV = srcOriginal.getParentFile().getParentFile().getAbsolutePath();
        File folder = new File(outputCSV, "ResultsCompareVariants");
        if (!folder.exists()) {
            folder.mkdir();
        }
        String fileStr = folder + File.separator + srcOriginal.getName() + ".csv";
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
                Boolean linecomment = false;
                while ((sCurrentLine = br.readLine()) != null) {
                    sCurrentLine = sCurrentLine.trim().replaceAll("\t", "").replaceAll("\r", "").replaceAll(" ", "");
                    if (!linecomment && !sCurrentLine.equals("%%") && !sCurrentLine.equals("") && !sCurrentLine.startsWith("//") && !sCurrentLine.startsWith("/*") && !sCurrentLine.startsWith("*/") && !sCurrentLine.startsWith("*") && !sCurrentLine.startsWith("import")) {
                        original.add(sCurrentLine);
                    } else if (sCurrentLine.startsWith("/*") && !sCurrentLine.contains("*/")) {
                        linecomment = true;
                    } else if (sCurrentLine.contains("*/") && linecomment) {
                        linecomment = false;
                    }
                }
                br.close();
                //compare text of files
                for (File fEcco : filesEcco) {
                    if (f.toPath().toString().substring(f.toPath().toString().indexOf("ecco\\") + 5).equals(fEcco.toPath().toString().substring(fEcco.toPath().toString().indexOf("checkout\\") + 9))) {
                        filenew = new File(String.valueOf(fEcco.toPath()));
                        br = new BufferedReader(new FileReader(filenew.getAbsoluteFile()));
                        linecomment = false;
                        while ((sCurrentLine = br.readLine()) != null) {
                            sCurrentLine = sCurrentLine.trim().replaceAll("\t", "").replaceAll("\r", "").replaceAll(" ", "");
                            if (!linecomment && !sCurrentLine.equals("%%") && !sCurrentLine.equals("") && !sCurrentLine.startsWith("//") && !sCurrentLine.startsWith("/*") && !sCurrentLine.startsWith("*/") && !sCurrentLine.startsWith("*") && !sCurrentLine.startsWith("import")) {
                                revised.add(sCurrentLine);
                            } else if (sCurrentLine.startsWith("/*") && !sCurrentLine.contains("*/")) {
                                linecomment = true;
                            } else if (sCurrentLine.contains("*/") && linecomment) {
                                linecomment = false;
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
                                        if (!line.equals("") && !line.startsWith("//") && !line.startsWith("/*") && !line.startsWith("*/") && !line.startsWith("*") && !line.equals("}")) {
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

                        Boolean found;
                        String trimmingDiffLinesalingmentOr = "";
                        for (String changedLine : changedLinesOriginal) {
                            found = false;
                            String aux = "";
                            for (String changedrevised : changedLinesRevised) {
                                if (changedrevised.contains("//#")) {
                                    aux = changedrevised.substring(0, changedrevised.indexOf("//#"));
                                    if (changedLine.equals(aux)) {
                                        found = true;
                                        aux = changedrevised;
                                        break;
                                    }
                                } else if (changedLine.equals(changedrevised) || changedLine.contains(changedrevised)) {
                                    found = true;
                                    aux = changedrevised;
                                    if (falsenegativeLines > 0)
                                        falsenegativeLines--;
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
                        ArrayList<String> diffinserted = new ArrayList<>();
                        found = false;
                        for (String line : deletedLines) {
                            for (String insertLine : insertedLines) {
                                if (insertLine.equals(line)) {
                                    if (falsepositiveLines > 0)
                                        falsepositiveLines--;
                                    if (falsenegativeLines > 0)
                                        falsenegativeLines--;
                                    found = true;
                                }
                                if (found) {
                                    diffinserted.add(insertLine);
                                    break;
                                }
                            }
                            if (!found) {
                                diffDeleted.add(line);
                            } else {
                                found = false;
                            }
                        }
                        insertedLines.removeAll(diffinserted);
                        deletedLines.removeAll(diffDeleted);


                        diffinserted = new ArrayList<>();
                        found = false;
                        for (String line : insertedLines) {
                            found = false;
                            for (String deletedLine : diffDeleted) {
                                if (deletedLine.equals(line) || deletedLine.contains(line)) {
                                    if (falsepositiveLines > 0)
                                        falsepositiveLines--;
                                    if (falsenegativeLines > 0)
                                        falsenegativeLines--;
                                    found = true;
                                }
                                if (found) {
                                    diffDeleted.remove(deletedLine);
                                    diffinserted.add(line);
                                    break;
                                }
                            }
                        }
                        insertedLines.removeAll(diffinserted);

                        ArrayList<String> diffChanged = new ArrayList<>();
                        found = false;
                        for (String line : changedLinesOriginal) {
                            for (String insertedLine : insertedLines) {
                                if (insertedLine.equals(line) || line.contains(insertedLine) || insertedLine.contains(line)) {
                                    if (falsepositiveLines > 0)
                                        falsepositiveLines--;
                                    if (falsenegativeLines > 0)
                                        falsenegativeLines--;
                                    found = true;
                                }
                                if (found) {
                                    found = false;
                                    diffChanged.add(insertedLine);
                                    break;
                                }
                            }
                        }
                        insertedLines.removeAll(diffChanged);
                        deletedLines = new ArrayList<>();
                        for (String del : diffDeleted) {
                            if (!del.equals("}") && !del.equals("{") && !del.equals(";") && !del.endsWith("*/")) {
                                deletedLines.add(del);
                                if (falsenegativeLines > 0)
                                    falsenegativeLines--;
                            }
                        }
                        falsenegativeLines = deletedLines.size();
                        if (falsepositiveLines == 0 && falsenegativeLines == 0)
                            matchFiles = true;
                        else {
                            if (deletedLines.size() > 0) {
                                for (String line : deletedLines) {
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
                        if (originaltotalLines == -1)
                            originaltotalLines = 0;
                        if (eccototalLines == -1)
                            eccototalLines = 0;
                        if (truepositiveLines == -1)
                            truepositiveLines = 0;
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
                    if (originaltotalLines == -1)
                        originaltotalLines = 0;
                    if (eccototalLines == -1)
                        eccototalLines = 0;
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
                        if (!sCurrentLine.equals("") && !sCurrentLine.equals("%%") && !sCurrentLine.startsWith("//") && !sCurrentLine.startsWith("/*") && !sCurrentLine.startsWith("*/") && !sCurrentLine.startsWith("*") && !sCurrentLine.startsWith("import")) {
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
                    if (originaltotalLines == -1)
                        originaltotalLines = 0;
                    if (eccototalLines == -1)
                        eccototalLines = 0;
                    if (truepositiveLines == -1)
                        truepositiveLines = 0;
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
            //for (String ext : this.fileTypes) {
            //    if (f.getName().endsWith("." + ext)) {
            if (!f.getName().equals(".config") && !f.getName().equals(".hashes") && !f.getName().equals(".warnings"))
                files.add(f);
            //    }
            //}
        }
    }


    public void eccoCheckout(ArrayList<String> configsToCheckout, Path OUTPUT_DIR) throws IOException {

        EccoService service = new EccoService();
        service.setRepositoryDir(OUTPUT_DIR.resolve("repo"));
        service.open();
        //checkout
        List<String> runtimes = new ArrayList<>();
        Long runtimeEccoCheckout, timeBefore, timeAfter;
        for (String config : configsToCheckout) {
            Path pathcheckout = Paths.get(OUTPUT_DIR.resolve("checkout") + File.separator + config);
            File checkoutfile = new File(String.valueOf(pathcheckout));
            //if (checkoutfile.exists()){
            //    Files.setAttribute(pathcheckout, "dos:readonly", false);
            //    GitCommitList.recursiveDelete(checkoutfile.toPath());
            //}
            checkoutfile.mkdir();
            service.setBaseDir(pathcheckout);
            timeBefore = System.currentTimeMillis();
            System.out.println("config: " + config);
            service.checkout(config);
            System.out.println("checked out!" + config);
            timeAfter = System.currentTimeMillis();
            runtimeEccoCheckout = timeAfter - timeBefore;
            runtimes.add("config: " + config + "  " + Long.toString(runtimeEccoCheckout));
            //String outputCSV = eccoFolder.getParentFile().getAbsolutePath();
            //String fileStr = outputCSV + File.separator + "runtime.csv";
            //BufferedReader csvReader = null;
            //try {
            //    csvReader = new BufferedReader(new FileReader(fileStr));
            //} catch (FileNotFoundException e) {
            //    e.printStackTrace();
            //}
            //String row = null;
            //ArrayList<String> listHeader = new ArrayList<>();
            //ArrayList<String> listRuntimeData = new ArrayList<>();
            //List<List<String>> rows = new ArrayList<>();

            //Boolean header = true;
            //while ((row = csvReader.readLine()) != null) {
            //    String[] data = row.split(",");
            //    ArrayList<String> dataAux = new ArrayList<>();
            //    if (header) {
            //        for (int i = 0; i < data.length; i++) {
            //            listHeader.add(data[i]);
            //        }
            //        header = false;
            //    } else {
            //        for (int i = 0; i < data.length; i++) {
            //            if ((data[1].equals(config.replace(",", "AND"))) && (i == 3)) {
            //                data[i] = (String.valueOf(runtimeEccoCheckout));
            //            }
            //            dataAux.add(data[i]);
            //        }
            //        rows.add(dataAux);
            //    }

            //}
            //csvReader.close();
            //File fwriter = new File(fileStr);
            //FileWriter csvWriter = new FileWriter(fwriter);

            //csvWriter.write(String.join(",", listHeader));
            //csvWriter.write("\n");
            //for (List<String> line : rows) {
            //    csvWriter.write(String.join(",", line));
            //    csvWriter.write("\n");
            //}
            //csvWriter.flush();
            //csvWriter.close();
        }
        //end checkout

        //close ecco repository
        service.close();
        Files.write(OUTPUT_DIR.resolve("timeCheckoutIndividual.txt"), runtimes.stream().map(Object::toString).collect(Collectors.toList()));
    }

    public void eccoCommit(File eccoFolder, Path OUTPUT_DIR, ArrayList<String> configsToCommit) throws IOException {
        EccoService service = new EccoService();
        service.setRepositoryDir(OUTPUT_DIR.resolve("repo"));
        //initializing repo
        service.init();
        //service.open();
        System.out.println("Repository initialized.");
        //commit
        List<String> runtimes = new ArrayList<>();
        Long runtimeEccoCommit, timeBefore, timeAfter;
        for (String config : configsToCommit) {
            //ecco commit
            System.out.println("CONFIG: " + config);
            File variantsrc = new File(eccoFolder, config);
            Path variant_dir = Paths.get(String.valueOf(variantsrc));
            service.setBaseDir(variant_dir);
            timeBefore = System.currentTimeMillis();
            service.commit(config);
            System.out.println("Committed: " + variant_dir);
            timeAfter = System.currentTimeMillis();
            runtimeEccoCommit = timeAfter - timeBefore;
            runtimes.add("config: " + config + "  " + Long.toString(runtimeEccoCommit));
            //end ecco commit
            /*String outputCSV = eccoFolder.getParentFile().getAbsolutePath();
            String fileStr = outputCSV + File.separator + "runtime.csv";
            BufferedReader csvReader = null;
            try {
                csvReader = new BufferedReader(new FileReader(fileStr));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            String row = null;
            ArrayList<String> listHeader = new ArrayList<>();
            ArrayList<String> listRuntimeData = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();

            Boolean header = true;
            while ((row = csvReader.readLine()) != null) {
                String[] data = row.split(",");
                ArrayList<String> dataAux = new ArrayList<>();
                if (header) {
                    for (int i = 0; i < data.length; i++) {
                        listHeader.add(data[i]);
                    }
                    header = false;
                } else {
                    for (int i = 0; i < data.length; i++) {
                        if ((data[1].equals(config.replace(",", "AND"))) && (i == 2)) {
                            data[i] = (String.valueOf(runtimeEccoCommit));
                        }
                        dataAux.add(data[i]);
                    }
                    rows.add(dataAux);
                }

            }
            csvReader.close();
            File fwriter = new File(fileStr);
            FileWriter csvWriter = new FileWriter(fwriter);

            csvWriter.write(String.join(",", listHeader));
            csvWriter.write("\n");
            for (List<String> line : rows) {
                csvWriter.write(String.join(",", line));
                csvWriter.write("\n");
            }
            csvWriter.flush();
            csvWriter.close();*/
        }
        //end commit

        //close ecco repository
        service.close();
        Files.write(OUTPUT_DIR.resolve("timeCommitIndividual.txt"), runtimes.stream().map(Object::toString).collect(Collectors.toList()));
    }

    //count SLOC
    @org.testng.annotations.Test
    public void countLinesOfCode() throws IOException {
        List<String> fileTypes = new LinkedList<String>();
        File gitFolder = new File("C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\CaseStudies\\clean");
        fileTypes.add("c");
        fileTypes.add("cpp");
        fileTypes.add("h");
        fileTypes.add("hpp");
        LinkedList<File> files = new LinkedList<>();
        getFilesToProcess(gitFolder, files);
        int countLines = 0;
        //files that are in ecco and variant
        for (File f : files) {
            List<String> original = new ArrayList<>();
            String extension = f.getName().substring(f.getName().lastIndexOf('.') + 1);
            if (fileTypes.contains(extension) && !f.isDirectory()) {
                try {
                    original = Files.readAllLines(f.toPath());
                } catch (IOException e) {
                    File filenew = new File(String.valueOf(f.toPath()));
                    BufferedReader br = null;
                    br = new BufferedReader(new FileReader(filenew.getAbsoluteFile()));
                    String sCurrentLine;
                    while ((sCurrentLine = br.readLine()) != null) {
                        original.add(sCurrentLine);
                    }
                    br.close();
                }
            }
            countLines += original.size();
        }
        System.out.println("Size: " + countLines);
    }

    @org.testng.annotations.Test
    public void getCSVInformation2() throws IOException {
        File folder = new File(csvcomparison_path);
        File[] lista = folder.listFiles();
        Float meanRunEccoCommit = Float.valueOf(0), meanRunEccoCheckout = Float.valueOf(0), meanRunPPCheckoutCleanVersion = Float.valueOf(0), meanRunPPCheckoutGenerateVariant = Float.valueOf(0), meanRunGitCommit = Float.valueOf(0), meanRunGitCheckout = Float.valueOf(0);
        Float totalnumberFiles = Float.valueOf(0), matchesFiles = Float.valueOf(0), eccototalLines = Float.valueOf(0), originaltotalLines = Float.valueOf(0), missingFiles = Float.valueOf(0), remainingFiles = Float.valueOf(0), totalVariantsMatch = Float.valueOf(0), truepositiveLines = Float.valueOf(0), falsepositiveLines = Float.valueOf(0), falsenegativeLines = Float.valueOf(0),
                truepositiveLinesEachFile = Float.valueOf(0), falsepositiveLinesEachFile = Float.valueOf(0), falsenegativeLinesEachFile = Float.valueOf(0), numberTotalFilesEachVariant = Float.valueOf(0), matchFilesEachVariant = Float.valueOf(0), eccototalLinesEachFile = Float.valueOf(0), originaltotalLinesEachFile = Float.valueOf(0);
        Boolean variantMatch = true;
        Float numberCSV = Float.valueOf(0);
        for (File file : lista) {
            if ((file.getName().indexOf(".csv") != -1) && !(file.getName().contains("features_report_each_project_commit")) && !(file.getName().contains("configurations"))) {
                Reader reader = Files.newBufferedReader(Paths.get(file.getAbsolutePath()));
                CSVReader csvReader = new CSVReaderBuilder(reader).build();
                List<String[]> matchesVariants = csvReader.readAll();

                if (file.getName().contains("runtime")) {
                    for (int i = 0; i < matchesVariants.size(); i++) {
                        if (i != 0) {
                            String[] runtimes = matchesVariants.get(i);
                            meanRunEccoCommit += Float.valueOf(runtimes[2]);
                            meanRunEccoCheckout += Float.valueOf(runtimes[3]);
                            meanRunPPCheckoutCleanVersion += Float.valueOf(runtimes[4]);
                            meanRunPPCheckoutGenerateVariant += Float.valueOf(runtimes[5]);
                            meanRunGitCommit += Float.valueOf(runtimes[6]);
                            meanRunGitCheckout += Float.valueOf(runtimes[7]);
                        }
                    }
                } else {
                    Float qtdLines = Float.valueOf(matchesVariants.size() - 4);
                    //totalnumberFiles += qtdLines;
                    for (int i = 1; i < matchesVariants.size(); i++) {

                        String[] line = matchesVariants.get(i);
                        truepositiveLines += Integer.valueOf(line[2]);
                        falsepositiveLines += Integer.valueOf(line[3]);
                        falsenegativeLines += Integer.valueOf(line[4]);
                        truepositiveLinesEachFile = Float.valueOf(Integer.valueOf(line[2]));
                        falsepositiveLinesEachFile = Float.valueOf(Integer.valueOf(line[3]));
                        falsenegativeLinesEachFile = Float.valueOf(Integer.valueOf(line[4]));
                        originaltotalLines += Integer.valueOf(line[5]);
                        eccototalLines += Integer.valueOf(line[6]);
                        originaltotalLinesEachFile = Float.valueOf(Integer.valueOf(line[5]));
                        eccototalLinesEachFile = Float.valueOf(Integer.valueOf(line[6]));
                        if (line[1].toUpperCase().equals("TRUE")) {
                            matchFilesEachVariant++;
                            matchesFiles++;
                            numberTotalFilesEachVariant += 1;
                        } else if (line[1].equals("not")) {
                            variantMatch = false;
                            missingFiles++;
                            numberTotalFilesEachVariant += 1;
                        } else if (line[1].equals("justOnRetrieved")) {
                            variantMatch = false;
                            remainingFiles++;
                        } else {
                            variantMatch = false;
                            missingFiles++;
                        }
                    }
                }
                numberCSV++;
                if (variantMatch && Float.compare(matchFilesEachVariant, numberTotalFilesEachVariant) == 0)
                    totalVariantsMatch++;
            }
            numberTotalFilesEachVariant = Float.valueOf(0);
            matchFilesEachVariant = Float.valueOf(0);
            variantMatch = true;
        }
        meanRunEccoCommit = (meanRunEccoCommit / numberCSV) / 1000;
        meanRunEccoCheckout = (meanRunEccoCheckout / numberCSV) / 1000;
        meanRunPPCheckoutCleanVersion = (meanRunPPCheckoutCleanVersion / numberCSV) / 1000;
        meanRunPPCheckoutGenerateVariant = (meanRunPPCheckoutGenerateVariant / numberCSV) / 1000;
        meanRunGitCommit = (meanRunGitCommit / numberCSV) / 1000;
        meanRunGitCheckout = (meanRunGitCheckout / numberCSV) / 1000;
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
        String filemetrics = "metrics_justdeleted.csv";
        //csv to report new features and features changed per git commit of the project
        try {
            FileWriter csvWriter = new FileWriter(resultMetrics_path + File.separator + filemetrics);
            List<List<String>> headerRows = Arrays.asList(
                    Arrays.asList("PrecisionVariant", "RecallVariant", "F1ScoreVariant", "PrecisionFiles", "RecallFiles", "F1ScoreFiles", "PrecisionLines", "RecalLines", "F1ScoreLines"),
                    Arrays.asList(precisionVariants.toString(), recallVariants.toString(), f1scoreVariants.toString(), precisionFiles.toString(), recallFiles.toString(), f1scoreFiles.toString(), precisionLines.toString(), recallLines.toString(), f1scorelines.toString()),
                    Arrays.asList("MeanRuntimeEccoCommit", "MeanRuntimeEccoCheckout", "MeanRuntimeGitCommit", "MeanRuntimeGitCheckout"),
                    Arrays.asList(meanRunEccoCommit.toString(), meanRunEccoCheckout.toString(), meanRunGitCommit.toString(), meanRunGitCheckout.toString())
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
        System.out.println(eccototalLines);
        System.out.println("Total lines inserted: " + falsepositiveLines + "\nTotal lines deleted: " + falsenegativeLines);
    }

}