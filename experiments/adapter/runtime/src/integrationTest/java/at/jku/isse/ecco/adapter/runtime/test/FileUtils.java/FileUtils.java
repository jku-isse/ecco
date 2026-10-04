package at.jku.isse.ecco.adapter.runtime.test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * General utils
 *
 * @author jabier.martinez
 */
public class FileUtils {
	/**
	 * Get lines of a file
	 *
	 * @param file
	 * @return list of strings
	 */
	public static List<String> getLinesOfFile(File file) {
		List<String> lines = new ArrayList<String>();
		try {
			FileInputStream fstream = new FileInputStream(file);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			while ((strLine = br.readLine()) != null) {
				lines.add(strLine);
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return lines;
	}

	/**
	 * Get string
	 *
	 * @param file
	 * @return
	 */
	public static String getStringOfFile(File file) {
		StringBuilder string = new StringBuilder();
		for (String line : getLinesOfFile(file)) {
			string.append(line + "\n");
		}
		string.setLength(string.length() - 1);
		return string.toString();
	}

	/**
	 * Get all files recursively (not folders) inside one folder. Or the file
	 * itself if the input is a file. Ignore the staging folder.
	 *
	 * @param dir
	 * @return list of files
	 */
	public static List<File> getAllJavaFilesIgnoringStagingFolder(File dir) {
		return getAllFiles(null, dir, true);
	}

	/**
	 * Get all files recursively (not folders) inside one folder. Or the file
	 * itself if the input is a file.
	 *
	 * @param dir
	 * @return
	 */
	public static List<File> getAllJavaFiles(File dir) {
		return getAllFiles(null, dir, false);
	}

	private static List<File> getAllFiles(List<File> files, File dir, boolean ignoreStaggingFolder) {
		if (files == null) {
			files = new ArrayList<File>();
		}

		if (!dir.isDirectory()) {
			if (dir.getName().endsWith(".java")) {
				files.add(dir);
			}
			return files;
		}

		for (File file : dir.listFiles()) {
			if (ignoreStaggingFolder) {
				if (!file.getName().equals("staging")) {
					getAllFiles(files, file, true);
				}
			} else {
				getAllFiles(files, file, false);
			}
		}
		return files;
	}

	/**
	 * Append line to file
	 *
	 * @param file
	 * @param text
	 * @throws Exception
	 */
	public static void appendToFile(File file, String text) throws Exception {
		BufferedWriter output;
		output = new BufferedWriter(new FileWriter(file, true));
		output.append(text);
		output.newLine();
		output.close();
	}

	/**
	 * No append, just overwrite with new text
	 *
	 * @param file
	 * @param text
	 * @throws Exception
	 */
	public static void writeFile(File file, String text) throws Exception {
		BufferedWriter output;
		output = new BufferedWriter(new FileWriter(file, false));
		output.append(text);
		output.close();
	}

	/**
	 * Copy file content (not directory) inside another file. It will replace
	 * the content if it already exists.
	 *
	 * @param sourceFile
	 * @param destinationFile
	 */
	public static void copyFile(File sourceFile, File destinationFile) {
		destinationFile.mkdirs();
		try {
			Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}
}
