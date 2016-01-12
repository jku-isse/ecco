package at.jku.isse.ecco.plugin.artifact.file;

import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import com.google.inject.Module;

import java.nio.file.Path;
import java.util.Set;

public class FilePlugin extends ArtifactPlugin {

	// private static final String[] fileTypes = new String[] { "" };

	private FileModule module = new FileModule();

	@Override
	public String getPluginId() {
		return FilePlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "FileArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "File Artifact Plugin";
	}

	@Override
	public ArtifactReader<Path, Set<Node>> createReader(final EntityFactory entityFactory) {
		return new FileReader(entityFactory);
	}

	@Override
	public ArtifactWriter<Set<Node>, Path> createWriter() {
		return new FileWriter();
	}

}
