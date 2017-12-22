package at.jku.isse.ecco.adapter.text.test;

import at.jku.isse.ecco.adapter.dispatch.DispatchModule;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.storage.perst.PerstModule;
import at.jku.isse.ecco.adapter.ArtifactPlugin;
import at.jku.isse.ecco.adapter.text.TextReader;
import at.jku.isse.ecco.adapter.text.TextFileWriter;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {

	public static final String REPOSITORY_DIR = ".ecco/";
	public static final String CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "ecco.db").toString();
	public static final String CLIENT_CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "client.db").toString();
	public static final String SERVER_CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "server.db").toString();

	public static void main(String[] args) {

		// delete database file
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


		// ################ YOUR CODE ################

		TextReader reader = injector.getInstance(TextReader.class);
		TextFileWriter writer = injector.getInstance(TextFileWriter.class);

		Path[] inputFiles = new Path[]{Paths.get("data/input/file.txt")};

		System.out.println("READ");
		Set<Node> nodes = reader.read(Paths.get("data/input"), inputFiles);

		// TODO: sequence the nodes?

		System.out.println("WRITE");
		Path[] outputFiles = writer.write(Paths.get("data/output"), nodes);

		// TODO: compare inputFiles with outputFiles

	}

}
