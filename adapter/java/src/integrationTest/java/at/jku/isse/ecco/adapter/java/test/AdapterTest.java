package at.jku.isse.ecco.adapter.java.test;

import at.jku.isse.ecco.adapter.java.JavaBlockReader;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class AdapterTest {

	@Test
	public void Java_Adapter_Test() {
		Path[] inputFiles = new Path[]{Paths.get("FigStateVertex.java")};

		JavaBlockReader reader = new JavaBlockReader(new SerEntityFactory());

		System.out.println("READ");
		Set<Node.Op> nodes = reader.read(Paths.get("C:\\Users\\gabil\\Desktop"), inputFiles);

		System.out.println(nodes);
	}
}
