package at.jku.isse.ecco.plugin.artifact.image;

import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import com.google.inject.Module;

import java.nio.file.Path;
import java.util.Set;

public class ImagePlugin extends ArtifactPlugin {

	private ImageModule module = new ImageModule();

	@Override
	public String getPluginId() {
		return ImagePlugin.class.getName();
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
		return new ImageReader(entityFactory);
	}

	@Override
	public ArtifactWriter<Set<Node>, Path> createWriter() {
		return new ImageWriter();
	}

}
