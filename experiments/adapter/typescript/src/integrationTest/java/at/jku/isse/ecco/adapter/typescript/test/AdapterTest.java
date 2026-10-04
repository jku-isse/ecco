package at.jku.isse.ecco.adapter.typescript.test;

import at.jku.isse.ecco.adapter.typescript.TypeScriptReader;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

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
	private static final Path[] FILES = new Path[]{Paths.get("parse.js")};

	/**
	 * Previously did not compile at all: imported org.testng.annotations.Test (testng isn't a
	 * dependency of this module, only JUnit) and at.jku.isse.ecco.storage.mem.dao.MemEntityFactory
	 * (no such class exists in the repo). Converted to JUnit 5 and SerEntityFactory to match every
	 * other adapter's AdapterTest - it now compiles and runs, but failed with
	 * "ENOENT ... chdir '.../adapter/typescript' -> '.../adapter/typescript/../adapter/typescript/
	 * src/main/resources/script'" from TypeScriptParser.parse().
	 * <p>
	 * That specific bug (parse.js's own location resolved via a hardcoded, working-directory-relative
	 * path that only worked if cwd happened to be exactly one directory below the repo root) is now
	 * fixed - the class's static initializer already extracted parse.js to a real file and remembered
	 * it correctly, TypeScriptParser.parse() just wasn't using it (a local variable of the same name
	 * was shadowing that field with the broken recomputation instead).
	 * <p>
	 * Still disabled: node_modules/typescript (the actual TypeScript compiler this depends on) isn't
	 * installed in this checkout - `npm install` was never run (there's a real Gradle npmInstall task
	 * wired up via the node-gradle plugin, `build.dependsOn npmInstall`, but that requires network
	 * access and a system Node.js, `download = false`). Even once that's installed,
	 * TypeScriptParser.NODE_MODULE_PATH has the identical hardcoded-relative-path fragility the
	 * parse.js bug had - a real question about how this adapter should locate its bundled npm
	 * dependency (dev/gradle-build context vs. a packaged install), not a one-line fix. Re-enable and
	 * re-verify once both of those are addressed.
	 */
	@Test
	@Disabled("node_modules/typescript is not installed in this checkout, and NODE_MODULE_PATH has the same working-directory-relative fragility parse.js's path had - see class javadoc")
	public void Java_Adapter_Test() {
		TypeScriptReader reader = new TypeScriptReader(new SerEntityFactory());

		Set<Node.Op> nodes = reader.read(BASE_DIR, FILES);

		assertEquals(1, nodes.size());
		assertTrue(nodes.iterator().next().getArtifact().toString().contains("parse.js"));
	}

}
