package at.jku.isse.ecco.adapter.challenge.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.challenge.JavaChallengeReader;
import at.jku.isse.ecco.adapter.challenge.data.MethodArtifactData;
import at.jku.isse.ecco.adapter.dispatch.DirectoryArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class AdapterTest {

	private static final Path BASE_DIR = Paths.get("C:\\Users\\gabil\\Desktop\\teste\\ActualECCO\\Method_Comparison\\Sudoku\\variants");
	private static final Path[] FILES = new Path[]{Paths.get("AbstractFilePersister.java")};

	@Test(groups = {"integration", "java"})
	public void Java_Adapter_Test() {
		JavaChallengeReader reader = new JavaChallengeReader(new MemEntityFactory());

		System.out.println("READ");
		Set<Node.Op> nodes = reader.read(BASE_DIR, FILES);

		System.out.println(nodes);
	}

	@Test(groups = {"integration", "java"})
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
