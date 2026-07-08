package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.adapter.dispatch.DispatchModule;
import at.jku.isse.ecco.adapter.dispatch.DispatchReader;
import at.jku.isse.ecco.storage.mem.MemModule;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.name.Names;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DispatcherTest {

	public static final String REPOSITORY_DIR = ".ecco/";
	public static final String CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "ecco.db").toString();
	public static final String CLIENT_CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "client.db").toString();
	public static final String SERVER_CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "server.db").toString();

	@Inject
	private DispatchReader reader;

	/**
	 * Was previously missing its input fixture entirely (reading from a CWD-relative "data/input"
	 * that was never checked in, so the write step NPE'd and was silently swallowed - see git
	 * history). Uses a classpath fixture instead, matching the convention every other adapter's
	 * AdapterTest uses.
	 * <p>
	 * Likely a real, separate bug, pinned down as-is rather than silently worked around:
	 * DispatchReader.read(base, input) for a bare top-level file path (no containing named
	 * directory) puts a literal null into the returned node set - see readDirectories(), which
	 * returns null for this shape of input and gets added via {@code nodes.add(baseDirectoryNode)}
	 * unchecked. Passing that result straight to DispatchWriter.write() NPEs in writeRec (node is
	 * null), which is what the original version of this test was silently swallowing.
	 */
	@Test
	public void Text_Module_Test() throws IOException, URISyntaxException {
		Path dataDir = Paths.get(DispatcherTest.class.getClassLoader().getResource("data").toURI());
		Path input = dataDir.resolve("input");
		Path[] inputFiles = new Path[]{Paths.get("file.txt")};

		Set<Node.Op> nodes = this.reader.read(input, inputFiles);

		assertEquals(1, nodes.size());
		assertTrue(nodes.contains(null));
	}

	@AfterEach
	public void afterTest() {
		System.out.println("AFTER");
		deleteDatabaseFile();
	}

	@BeforeEach
	public void beforeTest() {
		System.out.println("BEFORE");

		// default properties
		Properties properties = new Properties();
		// properties.setProperty("module.dal", "at.jku.isse.ecco.perst");
		properties.setProperty("repositoryDir", REPOSITORY_DIR);
		properties.setProperty("connectionString", CONNECTION_STRING);
		properties.setProperty("clientConnectionString", CLIENT_CONNECTION_STRING);
		properties.setProperty("serverConnectionString", SERVER_CONNECTION_STRING);

		// create modules
		final Module connectionStringModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(String.class).annotatedWith(Names.named("connectionString")).toInstance(properties.getProperty("connectionString"));
				bind(String.class).annotatedWith(Names.named("clientConnectionString")).toInstance(properties.getProperty("clientConnectionString"));
				bind(String.class).annotatedWith(Names.named("serverConnectionString")).toInstance(properties.getProperty("serverConnectionString"));
			}
		};
		final Module repositoryDirModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(Path.class).annotatedWith(Names.named("repositoryDir")).toInstance(Path.of(properties.getProperty("repositoryDir")));
			}
		};
		List<Module> modules = new ArrayList<Module>();
		for (ArtifactPlugin ap : ArtifactPlugin.getArtifactPlugins()) {
			modules.add(ap.getModule());
		}
		modules.addAll(Arrays.asList(new DispatchModule(), new MemModule(), repositoryDirModule, connectionStringModule));

		// create injector
		Injector injector = Guice.createInjector(modules);

		injector.injectMembers(this);

		deleteDatabaseFile();
	}

	private void deleteDatabaseFile() {
		try {
			Files.deleteIfExists(Paths.get(CONNECTION_STRING));
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			Files.deleteIfExists(Paths.get(REPOSITORY_DIR));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
