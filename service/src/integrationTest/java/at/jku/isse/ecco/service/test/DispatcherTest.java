package at.jku.isse.ecco.service.test;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.adapter.dispatch.DispatchModule;
import at.jku.isse.ecco.adapter.dispatch.DispatchReader;
import at.jku.isse.ecco.adapter.dispatch.DispatchWriter;
import at.jku.isse.ecco.storage.mem.MemModule;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.name.Names;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DispatcherTest {

	public static final String REPOSITORY_DIR = ".ecco/";
	public static final String CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "ecco.db").toString();
	public static final String CLIENT_CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "client.db").toString();
	public static final String SERVER_CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "server.db").toString();

	@Inject
	private DispatchReader reader;
	@Inject
	private DispatchWriter writer;

	@Test(groups = {"integration", "dispatcher"})
	public void Text_Module_Test() {
		Path[] inputFiles = new Path[]{Paths.get("variant1"), Paths.get("variant1/file.txt"), Paths.get("variant1/1.png"), Paths.get("variant1/subdir"), Paths.get("variant1/subdir/file")};

		System.out.println("READ");
		Set<Node.Op> nodes = this.reader.read(Paths.get("data/input"), inputFiles);

		// TODO: sequence the nodes?

		System.out.println("WRITE");
		Path[] outputFiles = this.writer.write(Paths.get("data/output"), nodes);

		// TODO: compare inputFiles with outputFiles
		for (Path outputFile : outputFiles) {
			System.out.println(outputFile);
		}
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
		deleteDatabaseFile();
	}

	@BeforeTest(alwaysRun = true)
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
				bind(String.class).annotatedWith(Names.named("repositoryDir")).toInstance(properties.getProperty("repositoryDir"));
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
