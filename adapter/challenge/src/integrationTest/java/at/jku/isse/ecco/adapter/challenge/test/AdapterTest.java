package at.jku.isse.ecco.adapter.challenge.test;

import at.jku.isse.ecco.adapter.challenge.JavaChallengeReader;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class AdapterTest {

	private static final Path BASE_DIR = Paths.get("C:\\Users\\user\\Desktop\\splc_challenge\\workspace\\argouml-app\\src\\org\\argouml\\persistence");
	private static final Path[] FILES = new Path[]{Paths.get("AbstractFilePersister.java")};

	@Test(groups = {"integration", "java"})
	public void Java_Adapter_Test() {
		JavaChallengeReader reader = new JavaChallengeReader(new MemEntityFactory());

		System.out.println("READ");
		Set<Node.Op> nodes = reader.read(BASE_DIR, FILES);

		System.out.println(nodes);
	}

}
