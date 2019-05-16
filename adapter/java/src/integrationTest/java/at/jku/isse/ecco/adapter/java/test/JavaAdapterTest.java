package at.jku.isse.ecco.adapter.java.test;

import at.jku.isse.ecco.adapter.java.JavaReader;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class JavaAdapterTest {

	@Test(groups = {"integration", "java"})
	public void Text_Module_Test() {
		Path[] inputFiles = new Path[]{Paths.get("test.java")};

		JavaReader reader = new JavaReader(new MemEntityFactory());

		System.out.println("READ");
		Set<Node.Op> nodes = reader.read(Paths.get("C:\\Users\\user\\Desktop\\eccotest"), inputFiles);


		System.out.println(nodes);
	}

}
