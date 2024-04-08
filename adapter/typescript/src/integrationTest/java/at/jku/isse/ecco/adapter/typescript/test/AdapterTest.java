package at.jku.isse.ecco.adapter.typescript.test;

import at.jku.isse.ecco.adapter.typescript.TypeScriptReader;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.testng.annotations.Test;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

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
	private static final Path[] FILES = new Path[]{Paths.get("parse.js")};

	@Test(groups = {"integration"})
	public void Java_Adapter_Test() {
		TypeScriptReader reader = new TypeScriptReader(new MemEntityFactory());

		System.out.println("READ");
		Set<Node.Op> nodes = reader.read(BASE_DIR, FILES);

		System.out.println(nodes);
	}

}
