package at.jku.isse.ecco.adapter.cpp.test;

import at.jku.isse.ecco.service.EccoService;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;

import java.io.*;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class FeatureRevisionLocationTest {
    //directory where you have the folder with the artifacts of the target systyem
    public final String resultsCSVs_path = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\CaseStudies\\Test500commits\\SQLite";
    //directory with the folder "variant_results" inside the folder with the artifacts of the target systyem
    public final String resultMetrics_path = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\CaseStudies\\Test500commits\\SQLite\\variant_results";
    //directory with the file "configurations.csv" inside the folder with the artifacts of the target systyem
    public final String configuration_path = "C:\\Users\\gabil\\Desktop\\PHD\\JournalExtensionEMSE\\CaseStudies\\Test500commits\\SQLite\\configurations.csv";
    //directory where you have the folder with the artifacts of Marlin target systyem
    public final String marlinFolder = "C:\\Users\\gabil\\Downloads\\SPLC2020-FeatureRevisionLocation-master\\Marlin";
    //directory where you have the folder with the artifacts of LibSSH target systyem
    public final String libsshFolder = "C:\\Users\\gabil\\Downloads\\SPLC2020-FeatureRevisionLocation-master\\LibSSH";
    //directory where you have the folder with the artifacts of SQLite target systyem
    public final String sqliteFolder = "C:\\Users\\gabil\\Downloads\\SPLC2020-FeatureRevisionLocation-master\\SQLite";
    //directory where you want to store the result file containing the metrics computed for all target systems
    public final String metricsResultFolder = "C:\\Users\\gabil\\Downloads\\SPLC2020-FeatureRevisionLocation-master";
    private List<String> fileTypes = new LinkedList<String>();


    //feature revision location where the traces are computed by the input containing a set of feature revisions (configuration) and its artifacts (variant source code)
    @org.testng.annotations.Test
    public void TestEccoCommit() throws IOException {
        ArrayList<String> configsToCommit = new ArrayList<>();
        File configuration = new File(configuration_path);
        Path OUTPUT_DIR = Paths.get(resultMetrics_path);
        File eccoFolder = new File(resultsCSVs_path+File.separator+"Input_variants"+File.separator);
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
        Path OUTPUT_DIR = Paths.get(resultMetrics_path+File.separator);
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
        File variantsrc = new File(resultsCSVs_path, "Input_variants");
        File checkoutfile = new File(resultMetrics_path, "checkout");
        try {
            for (File path : variantsrc.listFiles()) {
                compareVariant(path, new File(checkoutfile + File.separator + path.getName()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //get the metrics of each and for all the target projects together.
    //To compute the metrics of variants this is considering all the files match and to compute files metrics this is considering all the lines match
    @org.testng.annotations.Test
    public void GetCSVInformationTotalTest() throws IOException {
        //set into this list of File the folders with csv files resulted from the comparison of variants of each target project
        File[] folder = {new File(marlinFolder),
                new File(libsshFolder),
                new File(sqliteFolder)};
        //write metrics in a csv file
        String filemetrics = "RandomMetricsEachAndTogether-no-inserted-lines.csv";
        FileWriter csvWriter = new FileWriter( metricsResultFolder + File.separator + filemetrics);

        Float totalmeanRunEccoCommit = Float.valueOf(0), totalmeanRunEccoCheckout = Float.valueOf(0), totalmeanRunPPCheckoutCleanVersion = Float.valueOf(0), totalmeanRunPPCheckoutGenerateVariant = Float.valueOf(0), totalmeanRunGitCommit = Float.valueOf(0), totalmeanRunGitCheckout = Float.valueOf(0);
        Float totaltotalnumberFiles = Float.valueOf(0), totalmatchesFiles = Float.valueOf(0), totaleccototalLines = Float.valueOf(0), totaloriginaltotalLines = Float.valueOf(0), totalmissingFiles = Float.valueOf(0), totalremainingFiles = Float.valueOf(0), totaltotalVariantsMatch = Float.valueOf(0), totaltruepositiveLines = Float.valueOf(0), totalfalsepositiveLines = Float.valueOf(0), totalfalsenegativeLines = Float.valueOf(0),
                totaltruepositiveLinesEachFile = Float.valueOf(0), totalfalsepositiveLinesEachFile = Float.valueOf(0), totalfalsenegativeLinesEachFile = Float.valueOf(0), totalnumberTotalFilesEachVariant = Float.valueOf(0), totalmatchFilesEachVariant = Float.valueOf(0), totaleccototalLinesEachFile = Float.valueOf(0), totaloriginaltotalLinesEachFile = Float.valueOf(0),  totalnumberCSV = Float.valueOf(0);
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
                        totalmeanRunEccoCommit+=meanRunEccoCommit; totalmeanRunEccoCheckout+=meanRunEccoCheckout; totalmeanRunGitCommit+=meanRunGitCommit; totalmeanRunGitCheckout+=meanRunGitCheckout;
                        totalmeanRunPPCheckoutCleanVersion+=meanRunPPCheckoutCleanVersion; totalmeanRunPPCheckoutGenerateVariant+=meanRunPPCheckoutGenerateVariant;
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

                            totaltruepositiveLines+=truepositiveLines;
                            totalfalsepositiveLines+=falsepositiveLines;
                            totalfalsenegativeLines+=falsenegativeLines;
                            totaltruepositiveLinesEachFile+=totaltruepositiveLinesEachFile;
                            totalfalsepositiveLinesEachFile+=falsepositiveLinesEachFile;
                            totalfalsenegativeLinesEachFile+=falsenegativeLinesEachFile;
                            totaloriginaltotalLines+=originaltotalLines;
                            totaleccototalLines+=eccototalLines;
                            totaloriginaltotalLinesEachFile+=originaltotalLinesEachFile;
                            totaleccototalLinesEachFile+=eccototalLinesEachFile;

                            if (line[1].equals("true")) {
                                if (Float.compare(originaltotalLinesEachFile, eccototalLinesEachFile) == 0 && Float.compare(truepositiveLinesEachFile, originaltotalLinesEachFile) == 0) {
                                    matchFilesEachVariant++;
                                    totalmatchFilesEachVariant++;
                                    matchesFiles++;
                                    totalmatchesFiles++;
                                } else {
                                    missingFiles++;
                                    totalmissingFiles++;
                                    variantMatch = false;
                                }
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
        String fileStr = outputCSV + File.separator + srcOriginal.getName() + ".csv";
        File fWriter = new File(fileStr);
        FileWriter csvWriter = new FileWriter(fWriter);

        List<List<String>> headerRows = Arrays.asList(
                Arrays.asList("fileName", "matchFile", "truepositiveLines", "falsepositiveLines", "falsenegativeLines", "originaltotalLines", "eccototalLines")
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
            if (fileTypes.contains(extension)) {
                //compare text of files
                for (File fEcco : filesEcco) {
                    if (f.toPath().toString().substring(f.toPath().toString().indexOf("ecco\\") + 5).equals(fEcco.toPath().toString().substring(fEcco.toPath().toString().indexOf("checkout\\") + 9))) {
                        try {
                            original = Files.readAllLines(f.toPath());
                        } catch (MalformedInputException e) {
                            StringBuilder contentBuilder = new StringBuilder();
                            File filenew = new File(String.valueOf(f.toPath()));
                            BufferedReader br = new BufferedReader(new FileReader(filenew.getAbsoluteFile()));
                            String sCurrentLine;
                            while ((sCurrentLine = br.readLine()) != null) {
                                original.add(sCurrentLine);
                            }
                            br.close();
                        }
                        try {
                            revised = Files.readAllLines(fEcco.toPath());
                        } catch (MalformedInputException e) {
                            StringBuilder contentBuilder = new StringBuilder();
                            File filenew = new File(String.valueOf(fEcco.toPath()));
                            BufferedReader br = new BufferedReader(new FileReader(filenew.getAbsoluteFile()));
                            String sCurrentLine;
                            while ((sCurrentLine = br.readLine()) != null) {
                                revised.add(sCurrentLine);
                            }
                            br.close();
                        }

                        // Compute diff. Get the Patch object. Patch is the container for computed deltas.
                        Patch<String> patch = null;
                        patch = DiffUtils.diff(original, revised);
                        ArrayList<String> insertedLines = new ArrayList<>();
                        ArrayList<String> deletedLines = new ArrayList<>();
                        if (patch.getDeltas().size() == 0) {
                            //files match
                            matchFiles = true;
                        } else {
                            //matchFiles = false;
                            String del ="", insert = "";
                            for (Delta delta : patch.getDeltas()) {
                                Integer difLines = Math.abs(delta.getOriginal().getLines().size() - delta.getRevised().getLines().size());
                                //List<String> unifiedDiff = DiffUtils.generateUnifiedDiff(f.getName(), fEcco.getName(), original, patch, original.size());
                                String line = "";
                                if (delta.getType().toString().equals("INSERT")) {
                                    ArrayList<String> arraylines = (ArrayList<String>) delta.getRevised().getLines();
                                    for (String deltaaux : arraylines) {
                                        line = deltaaux.trim().replaceAll("\t", "").replaceAll(",", "").replaceAll(" ", "");
                                        if (!line.equals("")) {
                                            matchFiles = false;
                                            falsepositiveLines++;
                                            insert=line;
                                            insertedLines.add(insert);
                                            //falsepositiveLines += difLines;
                                            //System.out.println("file: " + fEcco.getAbsolutePath()  +" TYPE: " +delta.getType().toString() +"delta: " + deltaaux);
                                        }
                                    }
                                } else {
                                    ArrayList<String> arraylines = (ArrayList<String>) delta.getOriginal().getLines();
                                    for (String deltaaux : arraylines) {
                                        line = deltaaux.trim().replaceAll("\t", "").replaceAll(",", "").replaceAll(" ", "");
                                        if (!line.equals("")) {
                                            matchFiles = false;
                                            falsenegativeLines++;
                                            del=line;
                                            deletedLines.add(del);
                                            //falsenegativeLines += difLines;
                                            //System.out.println("file: " + fEcco.getAbsolutePath() +" TYPE: " +delta.getType().toString() +"delta: " + deltaaux);
                                        }
                                    }


                                }
                            }
                        }
                        ArrayList<String> diffDeleted = new ArrayList<>();
                        Boolean found = false;
                        for (String line: deletedLines) {
                            for (String insertLine: insertedLines) {
                                if(insertLine.equals(line)){
                                    falsepositiveLines--;
                                    falsenegativeLines--;
                                    found = true;
                                    break;
                                }
                            }
                            if(!found) {
                                diffDeleted.add(line);
                            }else {
                                insertedLines.remove(line);
                                found = false;
                            }
                        }

                        if (falsepositiveLines==0 && falsenegativeLines == 0)
                            matchFiles=true;
                        else {
                            if (diffDeleted.size() > 0) {
                                for (String line: diffDeleted) {
                                    System.out.println("file: " + fEcco.getAbsolutePath() + " TYPE: DELETE delta: " +line);
                                }
                            }
                            if (insertedLines.size() > 0) {
                                for (String line: insertedLines) {
                                    System.out.println("file: " + fEcco.getAbsolutePath() + " TYPE: INSERT delta: " +line);
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
                    List<List<String>> resultRows = Arrays.asList(
                            Arrays.asList(f.toPath().toString().substring(f.toPath().toString().indexOf("ecco\\") + 5).replace(",", "and"), "not", "0", "0", originaltotalLines.toString(), originaltotalLines.toString(), eccototalLines.toString())
                    );
                    for (List<String> rowData : resultRows) {
                        csvWriter.append(String.join(",", rowData));
                        csvWriter.append("\n");
                    }
                }
            } else {
                //compare other type files
                for (File fEcco : filesEcco) {
                    if (f.toPath().toString().substring(f.toPath().toString().indexOf("ecco\\") + 5).equals(fEcco.toPath().toString().substring(fEcco.toPath().toString().indexOf("checkout\\") + 9))) {
                        if (!f.isDirectory()) {
                            int byte_f1;
                            int byte_f2;
                            if (f.length() == fEcco.length()) {
                                try {
                                    InputStream isf1 = new FileInputStream(f);
                                    InputStream isf2 = new FileInputStream(fEcco);
                                    matchFiles = true;
                                    for (long i = 0; i <= f.length(); i++) {
                                        try {
                                            byte_f1 = isf1.read();
                                            byte_f2 = isf2.read();
                                            if (byte_f1 != byte_f2) {
                                                isf1.close();
                                                isf2.close();
                                                // tamanhos iguais e conteudos diferentes
                                                matchFiles = false;
                                            }
                                        } catch (IOException ex) {
                                        }
                                    }
                                } catch (FileNotFoundException ex) {
                                }
                                if (!matchFiles) {
                                    falsenegativeLines = 1;
                                    truepositiveLines = 0;
                                    falsepositiveLines = 0;
                                } else {
                                    // arquivos iguais
                                    truepositiveLines = 1;
                                    falsenegativeLines = 0;
                                    falsepositiveLines = 0;
                                }
                            } else {
                                // tamanho e conteudo diferente
                                matchFiles = false;
                                falsenegativeLines = 1;
                                truepositiveLines = 0;
                                falsepositiveLines = 0;
                            }


                        } else {
                            truepositiveLines = 1;
                            falsenegativeLines = 0;
                            falsepositiveLines = 0;
                            fileExistsInEcco = true;
                            matchFiles = true;
                        }
                        eccototalLines = 1;
                        originaltotalLines = 1;

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
                    falsenegativeLines = 1;
                    falsepositiveLines = 0;
                    truepositiveLines = 0;
                    List<List<String>> resultRows = Arrays.asList(
                            Arrays.asList(f.toPath().toString().substring(f.toPath().toString().indexOf("ecco\\") + 5).replace(",", "and"), "not", truepositiveLines.toString(), falsepositiveLines.toString(), falsenegativeLines.toString(), originaltotalLines.toString(), eccototalLines.toString())
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
            Boolean fileExistsInEcco = false;
            Integer truepositiveLines = 0, falsepositiveLines = 0, falsenegativeLines = 0, originaltotalLines = 0, eccototalLines = 0;
            Boolean matchFiles = false;

            String extension = fEcco.getName().substring(fEcco.getName().lastIndexOf('.') + 1);
            if (fileTypes.contains(extension)) {

                //compare text of files
                List<String> original = Files.readAllLines(fEcco.toPath());
                Boolean existJustEcco = true;
                for (File f : filesVariant) {
                    if (fEcco.toPath().toString().substring(fEcco.toPath().toString().indexOf("checkout\\") + 9).equals(f.toPath().toString().substring(f.toPath().toString().indexOf("ecco\\") + 5))) {
                        existJustEcco = false;
                    }
                }
                //file just exist in ecco
                if (existJustEcco) {

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
            } else {
                //compare other type files
                Boolean existJustEcco = true;
                for (File f : filesVariant) {
                    if (fEcco.toPath().toString().substring(fEcco.toPath().toString().indexOf("checkout\\") + 9).equals(f.toPath().toString().substring(f.toPath().toString().indexOf("ecco\\") + 5))) {
                        existJustEcco = false;
                    }
                }
                if (existJustEcco) {
                    matchFiles = false;
                    eccototalLines = 1;
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
            System.out.println("checked out!"+ config);
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

}