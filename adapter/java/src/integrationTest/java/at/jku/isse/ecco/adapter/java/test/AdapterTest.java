package at.jku.isse.ecco.adapter.java.test;

import at.jku.isse.ecco.adapter.java.JavaBlockReader;
import at.jku.isse.ecco.adapter.java.JavaReader;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.testng.annotations.Test;

import java.io.FileNotFoundException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

public class AdapterTest {

	private static final String FILE_PATH = "C:\\Users\\gabil\\eclipse-workspace\\ArgoUMLSPLBenchmark\\scenarios\\ScenarioRandom002Variants\\variants\\00001.config\\src";


	@Test(groups = {"integration", "java"})
	public void Java_Adapter_Test() {
		Path[] inputFiles = new Path[]{Paths.get("FigStateVertex.java")};

		JavaBlockReader reader = new JavaBlockReader(new MemEntityFactory());

		System.out.println("READ");
		Set<Node.Op> nodes = reader.read(Paths.get("C:\\Users\\gabil\\Desktop"), inputFiles);

		System.out.println(nodes);

		//Path source = Paths.get(FILE_PATH);
		//try {
		//	Files.walkFileTree(source, new MyFileVisitor());
		//} catch (IOException e) {
		//	e.printStackTrace();
		//}
	}


	static class MyFileVisitor extends SimpleFileVisitor<Path> {
		Path[] inputFiles;

		public FileVisitResult visitFile(Path path, BasicFileAttributes fileAttributes) throws FileNotFoundException {
			String auxFileJava = path.getFileName().toString();
			if (auxFileJava.endsWith(".java")) {
				inputFiles = new Path[]{Paths.get(path.getFileName().toString())};

			}
			JavaReader reader = new JavaReader(new MemEntityFactory());
			Set<Node.Op> nodes = reader.read(Paths.get(String.valueOf(path)), inputFiles);
			System.out.println(nodes);
			return FileVisitResult.CONTINUE;
		}

		public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes fileAttributes) {

			return FileVisitResult.CONTINUE;
		}
	}

}
