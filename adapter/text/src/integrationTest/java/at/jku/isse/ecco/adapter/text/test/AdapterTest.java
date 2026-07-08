package at.jku.isse.ecco.adapter.text.test;

import at.jku.isse.ecco.adapter.text.*;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.*;
import at.jku.isse.ecco.util.Trees;
import org.junit.jupiter.api.*;

import java.net.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdapterTest {

	private static final Path DATA_DIR;

	static {
		Path dataPath = null;
		try {
			dataPath = Paths.get(AdapterTest.class.getClassLoader().getResource("data").toURI());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		DATA_DIR = dataPath;
	}

	private static final Path BASE_DIR = DATA_DIR.resolve("input");
	private static final Path[] FILES = new Path[]{Paths.get("file.txt")};

	@Test
	public void Java_Adapter_Test() {
		TextReader reader = new TextReader(new SerEntityFactory());

		Set<Node.Op> nodes = reader.read(BASE_DIR, FILES);

		assertEquals(1, nodes.size());
		Node.Op fileNode = nodes.iterator().next();
		assertTrue(fileNode.getArtifact().toString().contains("file.txt"));
		// file.txt (in src/integrationTest/resources/data/input) has 4 lines; +1 for the file node itself
		assertEquals(5, Trees.countArtifacts(fileNode));
	}

}
