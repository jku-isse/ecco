package at.jku.isse.ecco.exporter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import at.jku.isse.ecco.module.Condition;

/**
 * 
 * @author Simon Eilmsteiner
 *
 */
public class FileOperations {

	
	public void insertAtLineNumber(Map<Condition, Integer> lines, Path path) {
		try	(BufferedWriter bufferedWriter = Files.newBufferedWriter(path)) {
			
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	
	public void insertAtEqualText(Map<Condition, String> lines) {
		
	}
	
	public void close() {
		
	}

}
