package at.jku.isse.ecco.runtime.test;

import at.jku.isse.ecco.dao.PerstEntityFactory;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.plugin.artifact.DispatchWriter;
import at.jku.isse.ecco.plugin.artifact.java.JavaReader;
import at.jku.isse.ecco.plugin.artifact.java.JavaWriter;
import at.jku.isse.ecco.plugin.artifact.runtime.RuntimeReader;
import at.jku.isse.ecco.plugin.artifact.runtime.RuntimeWriter;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class RuntimeModuleTest {

	public static void main(String[] args) {
		RuntimeReader reader = new RuntimeReader(new PerstEntityFactory(), new JavaReader(new PerstEntityFactory()));
		RuntimeWriter runtimeWriter = new RuntimeWriter();
		JavaWriter javaWriter = new JavaWriter();

		Path[] inputFiles = new Path[]{Paths.get(".settings.config")};

		System.out.println("READ");
		Set<Node> nodes = reader.read(Paths.get("data/input"), inputFiles);

		System.out.println("WRITE");
		Path[] outputFiles = runtimeWriter.write(Paths.get("data/output"), nodes);

		outputFiles = javaWriter.write(Paths.get("data/output"), nodes);
	}

	@Test(groups = {"test", "runtime"})
	public void Runtime_Module_Test() {
		RuntimeReader reader = new RuntimeReader(new PerstEntityFactory(), new JavaReader(new PerstEntityFactory()));

		RuntimeWriter runtimeWriter = new RuntimeWriter();
		JavaWriter javaWriter = new JavaWriter();

		Set<ArtifactWriter<Set<Node>, Path>> writers = new HashSet<>();
		writers.add(runtimeWriter);
		writers.add(javaWriter);
		DispatchWriter writer = new DispatchWriter(writers, "");

		Path[] inputFiles = new Path[]{Paths.get(".settings.config")};

		System.out.println("READ");
		Set<Node> nodes = reader.read(Paths.get("data/input"), inputFiles);

		for (Node node : nodes)
			Trees.print(node);

		System.out.println("WRITE");
		writer.write(Paths.get("data/output"), nodes);
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");
	}

}
