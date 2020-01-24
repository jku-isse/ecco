package at.jku.isse.ecco.adapter.challenge.test;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Metrics calculation
 * <p>
 * Precision and Recall
 * <p>
 * Precision: Have I retrieved a lot of incorrect results?
 * <p>
 * Recall: Have I found them?
 * <p>
 * F1: Accuracy metric that combines precision and recall
 *
 * @author jabier.martinez
 */
public class MetricsCalculation {

	private static final String EXTENSION = ".txt";

	public static void computeMetrics(Path groundTruthDir, Path scenarioOutputDir) {
		System.out.println("Metrics calculation");
		File groundTruth = groundTruthDir.toFile();
		File yourResults = scenarioOutputDir.resolve("results").toFile();
		File yourResultsMetrics = scenarioOutputDir.toFile();
		String results = getResults(groundTruth, yourResults);

		File resultsFile = new File(yourResultsMetrics, "resultPrecisionRecall_" + System.currentTimeMillis() + ".csv");
		File metricsFile = scenarioOutputDir.resolve("metrics.txt").toFile();
		try {
			FileUtils.writeFile(resultsFile, results);
			FileUtils.writeFile(metricsFile, averagePrecision + ";" + averageRecall + ";" + averageF1);
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Results file created");
		System.out.println("Gnuplot script:");

		System.out.println("cd '" + resultsFile.getAbsoluteFile().getParentFile().getAbsolutePath() + "'");
		System.out.println("set style data boxplot");
		System.out.println("set datafile sep ','");
		System.out.println("set style boxplot outliers pointtype 6");
		System.out.println("set style fill empty");
		System.out.println("set xtics ('Names' 1, 'Precision' 2, 'Recall' 3, 'FScore' 4) scale 0.0");
		System.out.println("set yrange [-0.04:1.04]");
		System.out.println("set title \"Actual features where nothing was retrieved= " + (int) failedToRetrieve_counter
				+ " out of 24\\nInexistent features where something was retrieved= "
				+ retrievedInexistentFeature_counter + "\\nMetrics for actual features:\"");
		// [i=2:4] because the first column is for names
		// every ::1::24 to ignore the last row with the global results
		System.out.println("plot for [i=2:4] '" + resultsFile.getName()
				+ "' every ::1::24 using (i):i notitle pointsize .8 lc rgb 'black'");
	}

	private static int retrievedInexistentFeature_counter;
	private static double failedToRetrieve_counter;

	private static double averagePrecision, averageRecall, averageF1;

	/**
	 * Create results file and store it in the actual folder parent folder
	 *
	 * @param actualFolder    containing txt files with the actual values
	 * @param retrievedFolder containing txt files with the retrieved values
	 */
	public static String getResults(File actualFolder, File retrievedFolder) {
		StringBuilder resultsContent = new StringBuilder();
		resultsContent.append("Name,Precision,Recall,FScore,FeaturesWithoutRetrieved,InexistentFeaturesRetrieved\n");
		double precisionAvg = 0;
		failedToRetrieve_counter = 0;
		double recallAvg = 0;
		double f1measureAvg = 0;
		double numberOfActualFiles = 0;
		// Go through the ground-truth folder
		for (File f : actualFolder.listFiles()) {
			// be sure that it is a correct file
			if (f.getName().endsWith(EXTENSION)) {
				numberOfActualFiles++;
				List<String> actualLines = FileUtils.getLinesOfFile(f);
				if (!actualLines.isEmpty()) {
					List<String> retrievedLines = null;
					// get its counterpart in the retrieved folder
					File f2 = new File(retrievedFolder, f.getName());
					if (f2.exists()) {
						retrievedLines = FileUtils.getLinesOfFile(f2);
					} else {
						// no file was created so it did not find anything
						retrievedLines = new ArrayList<String>();
					}

					// Calculate metrics
					double precision = getPrecision(actualLines, retrievedLines);
					double recall = getRecall(actualLines, retrievedLines);
					double f1measure = getF1(precision, recall);

					// Append the row to the results file
					// get the name by removing the file extension
					String name = f.getName().substring(0, f.getName().length() - EXTENSION.length());
					resultsContent.append(name + ",");

					// precision
					if (Double.isNaN(precision)) {
						resultsContent.append("0,");
					} else {
						precisionAvg += precision;
						resultsContent.append(precision + ",");
					}

					// recall
					if (Double.isNaN(recall)) {
						resultsContent.append("0,");
					} else {
						recallAvg += recall;
						resultsContent.append(recall + ",");
					}

					// f1score
					if (Double.isNaN(f1measure)) {
						resultsContent.append("0,");
					} else {
						f1measureAvg += f1measure;
						resultsContent.append(f1measure + ",");
					}

					// something retrieved or not
					if (retrievedLines.isEmpty()) {
						failedToRetrieve_counter++;
						resultsContent.append("NothingRetrieved\n");
					} else {
						resultsContent.append("SomethingRetrieved\n");
					}
				}
			}
		}

		resultsContent.append("Average,");
		// precision avg.
		precisionAvg = precisionAvg / numberOfActualFiles;
		if (Double.isNaN(precisionAvg)) {
			resultsContent.append("0,");
		} else {
			resultsContent.append(precisionAvg + ",");
		}

		// recall avg.
		recallAvg = recallAvg / numberOfActualFiles;
		if (Double.isNaN(recallAvg)) {
			resultsContent.append("0,");
		} else {
			resultsContent.append(recallAvg + ",");
		}

		// f1score avg.
		f1measureAvg = f1measureAvg / numberOfActualFiles;
		if (Double.isNaN(f1measureAvg)) {
			resultsContent.append("0,");
		} else {
			resultsContent.append(f1measureAvg + ",");
		}

		averagePrecision = precisionAvg;
		averageRecall = recallAvg;
		averageF1 = f1measureAvg;

		// total failed to retrieve
		resultsContent.append(failedToRetrieve_counter + ",");

		// Check retrieved but inexistent in the actual folder
		StringBuilder inexistent = new StringBuilder();
		retrievedInexistentFeature_counter = 0;
		for (File f : retrievedFolder.listFiles()) {
			File fInActualFolder = new File(actualFolder, f.getName());
			if (f.getName().endsWith(EXTENSION) && !fInActualFolder.exists()) {
				// it does not exist in actual folder
				retrievedInexistentFeature_counter++;
				String name = f.getName().substring(0, f.getName().length() - EXTENSION.length());
				inexistent.append(name);
				inexistent.append(",");
			}
		}
		resultsContent.append(retrievedInexistentFeature_counter + ",");
		if (retrievedInexistentFeature_counter > 0) {
			// remove last comma
			inexistent.setLength(inexistent.length() - 1);
			// append list
			resultsContent.append(inexistent.toString());
		}

		return resultsContent.toString();
	}

	/**
	 * From the retrieved elements, those that are on the actual list
	 *
	 * @param actualLines
	 * @param retrievedLines
	 * @return
	 */
	public static List<String> getTruePositives(List<String> actualLines, List<String> retrievedLines) {
		List<String> found = new ArrayList<String>();
		for (String a : retrievedLines) {
			if (actualLines.contains(a)) {
				found.add(a);
			}
		}
		return found;
	}

	/**
	 * From the retrieved elements, those that are not on the actual list
	 *
	 * @param actualLines
	 * @param retrievedLines
	 * @return
	 */
	public static List<String> getFalsePositives(List<String> actualLines, List<String> retrievedLines) {
		List<String> found = new ArrayList<String>();
		for (String a : retrievedLines) {
			if (!actualLines.contains(a)) {
				found.add(a);
			}
		}
		return found;
	}

	public static double getPrecision(List<String> actualLines, List<String> retrievedLines) {
		List<String> truePositives = getTruePositives(actualLines, retrievedLines);
		List<String> falsePositives = getFalsePositives(actualLines, retrievedLines);
		double precision = (double) truePositives.size()
				/ (double) ((double) truePositives.size() + (double) falsePositives.size());
		return precision;
	}

	public static double getRecall(List<String> actualLines, List<String> retrievedLines) {
		List<String> truePositives = getTruePositives(actualLines, retrievedLines);
		double recall = (double) truePositives.size() / (double) actualLines.size();
		return recall;
	}

	public static double getF1(double precision, double recall) {
		double f1 = 2 * ((precision * recall) / (precision + recall));
		return f1;
	}

}
