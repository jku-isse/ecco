package at.jku.isse.ecco.adapter.challenge.test;

import at.jku.isse.ecco.adapter.challenge.*;
import at.jku.isse.ecco.storage.mem.dao.*;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class AdapterTest {

	private static final Path BASE_DIR = Paths.get("C:\\Users\\gabil\\Desktop\\teste\\ActualECCO\\Method_Comparison\\Sudoku\\variants");
	private static final Path[] FILES = new Path[]{Paths.get("AbstractFilePersister.java")};

	@Test
	public void Java_Adapter_Test() {
		JavaChallengeReader reader = new JavaChallengeReader(new MemEntityFactory());

		System.out.println("READ");
		Set<Node.Op> nodes = reader.read(BASE_DIR, FILES);

		System.out.println(nodes);
	}

	@Test
	public void ReadMethods(){
		JavaChallengeReader reader = new JavaChallengeReader(new MemEntityFactory());
		System.out.println("READ");
		File dir = new File("C:\\Users\\gabil\\Desktop\\teste\\ActualECCO\\Method_Comparison\\Notepad\\variants");
		File[] variants = dir.listFiles();
		for (File var : variants) {
			ArrayList<String> methods = new ArrayList<>();
			if(var.isDirectory()){
				File[] files = new File(var,"Notepad").listFiles();
				for (File file : files) {
					Path[] inputFiles = new Path[]{Paths.get(String.valueOf(file))};
					System.out.println("READ");
					Set<Node.Op> nodes = reader.read(Paths.get("BASE_DIR"), inputFiles, methods);
					System.out.println(nodes);
				}
			}
			//these lines were added to compute the results at method level
			try {
				Files.write(Paths.get(dir+"\\"+(var.getName()+".txt")), methods.stream().map(Object::toString).collect(Collectors.toList()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
