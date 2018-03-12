package at.jku.isse.ecco.adapter.file.test;

import at.jku.isse.ecco.adapter.dispatch.DispatchModule;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.storage.perst.PerstModule;
import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.adapter.file.FileReader;
import at.jku.isse.ecco.adapter.file.FileWriter;
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

public class FileModuleTest {

	public static final String REPOSITORY_DIR = ".ecco/";
	public static final String CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "ecco.db").toString();
	public static final String CLIENT_CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "client.db").toString();
	public static final String SERVER_CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "server.db").toString();

	@Inject
	private FileReader reader;
	@Inject
	private FileWriter writer;

	@Test(groups = {"integration", "text"})
	public void Text_Module_Test() {
		Path[] inputFiles = new Path[]{Paths.get("data/input/file")};

		System.out.println("READ");
		Set<Node> nodes = this.reader.read(Paths.get("data/input"), inputFiles);

		// TODO: sequence the nodes?

		System.out.println("WRITE");
		Path[] outputFiles = this.writer.write(Paths.get("data/output"), nodes);

		// TODO: compare inputFiles with outputFiles
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
		modules.addAll(Arrays.asList(new DispatchModule(), new PerstModule(), repositoryDirModule, connectionStringModule));

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

//		File dbFile = new File(CONNECTION_STRING);
//		if (dbFile.exists() && !dbFile.delete()) {
//			System.out.println("Could not delete the database file.");
//		}
//		File repoDir = new File(REPOSITORY_DIR);
//		if (repoDir.exists() && !repoDir.delete()) {
//			System.out.println("Could not delete the repository directory.");
//		}
	}

}
