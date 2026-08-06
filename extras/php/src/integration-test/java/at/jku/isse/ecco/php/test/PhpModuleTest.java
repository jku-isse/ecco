package at.jku.isse.ecco.php.test;

import at.jku.isse.ecco.dao.PerstEntityFactory;
import at.jku.isse.ecco.plugin.artifact.php.PhpReader;
import at.jku.isse.ecco.plugin.artifact.php.PhpWriter;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * @author Timea Kovacs
 */
public class PhpModuleTest {

	public static final String REPOSITORY_DIR = ".ecco/";
	public static final String CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "ecco.db").toString();
	public static final String CLIENT_CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "client.db").toString();
	public static final String SERVER_CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "server.db").toString();

	@Inject
	private PhpReader reader;
	@Inject
	private PhpWriter writer;

	@Test(groups = {"integration", "php", "read"})
	public void Php_Reader() {
		System.out.println("Starting...");
		Path[] files = new Path[]{Paths.get("data/input/test.php")};
		this.reader.read(files);
	}

	@Test(groups = {"integration", "php", "read", "write"})
	public void Php_Reader_Writer() {
		System.out.println("Starting...");
		Path[] files = new Path[]{Paths.get("test.php")};
		Set<Node> nodes = this.reader.read(Paths.get("data/input"), files);
		this.writer.write(Paths.get("data/output"), nodes);
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");

//		deleteDatabaseFile();
	}

	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");

		this.reader = new PhpReader(new PerstEntityFactory());
		this.writer = new PhpWriter();


//		// default properties
//		Properties properties = new Properties();
//		// properties.setProperty("module.dal", "at.jku.isse.ecco.perst");
//		properties.setProperty("repositoryDir", REPOSITORY_DIR);
//		properties.setProperty("connectionString", CONNECTION_STRING);
//		properties.setProperty("clientConnectionString", CLIENT_CONNECTION_STRING);
//		properties.setProperty("serverConnectionString", SERVER_CONNECTION_STRING);
//
//		// create modules
//		final Module connectionStringModule = new AbstractModule() {
//			@Override
//			protected void configure() {
//				bind(String.class).annotatedWith(Names.named("connectionString")).toInstance(properties.getProperty("connectionString"));
//				bind(String.class).annotatedWith(Names.named("clientConnectionString")).toInstance(properties.getProperty("clientConnectionString"));
//				bind(String.class).annotatedWith(Names.named("serverConnectionString")).toInstance(properties.getProperty("serverConnectionString"));
//			}
//		};
//		final Module repositoryDirModule = new AbstractModule() {
//			@Override
//			protected void configure() {
//				bind(String.class).annotatedWith(Names.named("repositoryDir")).toInstance(properties.getProperty("repositoryDir"));
//			}
//		};
//		List<Module> modules = new ArrayList<Module>();
//		for (ArtifactPlugin ap : ArtifactPlugin.getArtifactPlugins()) {
//			modules.add(ap.getModule());
//		}
//		modules.addAll(Arrays.asList(new CoreModule(), new PerstModule(), repositoryDirModule, connectionStringModule));
//
//		// create injector
//		Injector injector = Guice.createInjector(modules);
//
//		injector.injectMembers(this);
//
//		deleteDatabaseFile();
	}

//	private void deleteDatabaseFile() {
//		File dbFile = new File(CONNECTION_STRING);
//		if (dbFile.exists() && !dbFile.delete()) {
//			System.out.println("Could not delete the database file.");
//		}
//		File repoDir = new File(REPOSITORY_DIR);
//		if (repoDir.exists() && !repoDir.delete()) {
//			System.out.println("Could not delete the repository directory.");
//		}
//	}

}
