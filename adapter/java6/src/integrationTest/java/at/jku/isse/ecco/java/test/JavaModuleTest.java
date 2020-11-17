package at.jku.isse.ecco.java.test;

import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.dao.PerstEntityFactory;
import at.jku.isse.ecco.adapter.java.JavaReader;
import at.jku.isse.ecco.adapter.java.JavaWriter;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;

public class JavaModuleTest {

	@Test(groups = {"integration", "java"})
	public void Java_Module_Test() {
		JavaReader javaReader = new JavaReader(new PerstEntityFactory());


		Set<Node> nodes = javaReader.read(Paths.get("data/input"), new Path[]{Paths.get("test.java"), Paths.get("test/testf.java")});
		for (Node node : nodes) {
			Trees.print(node);
		}

//		Set<Node> nodes2 = javaReader.read(new Path[]{Paths.get("data/input/test.java")});
//		Trees.print(nodes2.iterator().next());


		JavaWriter javaWriter = new JavaWriter();
		javaWriter.write(Paths.get("data/output"), nodes);
	}

	@Test(groups = {"integration", "java"})
	public void Java_Module_Min_Test() {
		JavaReader javaReader = new JavaReader(new PerstEntityFactory());


		Set<Node> nodes = javaReader.read(Paths.get("data/input"), new Path[]{Paths.get("test2.java")});
		for (Node node : nodes) {
			print(node);
		}

		JavaWriter javaWriter = new JavaWriter();
		javaWriter.write(Paths.get("data/output"), nodes);
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");
	}

	public static void print(Node node) {
		printRec(node, "");
	}

	private static void printRec(Node node, String indent) {
		String detailString = "";
		if (node.getArtifact() != null) {
			detailString = "[" + node.getArtifact().isAtomic() + "]" + "(" + node.getArtifact().getUses().stream().map((ArtifactReference ref) -> "REF: " + ref.getTarget().toString() + ", ID: " + System.identityHashCode(ref.getTarget())).collect(Collectors.joining(", ")) + ")" + "ID: " + System.identityHashCode(node.getArtifact());
		}
		System.out.println(indent + node.toString() + detailString);
		String newIndent = indent + " ";
//		if (node.isAtomic())
//			return;
		for (Node child : node.getChildren()) {
			printRec(child, newIndent);
		}
	}

}
