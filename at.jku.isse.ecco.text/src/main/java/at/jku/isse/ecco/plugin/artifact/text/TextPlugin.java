package at.jku.isse.ecco.plugin.artifact.text;

import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import com.google.inject.Module;

import java.nio.file.Path;
import java.util.Set;

public class TextPlugin extends ArtifactPlugin {

	private static final String[] fileTypes = new String[]{"txt", "xml", "tex", "java", "c", "h", "cpp", "hpp"};

	private TextModule module = new TextModule();

	public String[] getFileTypes() {
		return fileTypes;
	}

	@Override
	public String getPluginId() {
		return TextPlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "TextArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "Text Artifact Plugin";
	}

	@Override
	public ArtifactReader<Path, Set<Node>> createReader(final EntityFactory entityFactory) {
		return new TextReader(entityFactory);
	}

	@Override
	public ArtifactWriter<Set<Node>, Path> createWriter() {
		return new TextWriter();
	}

}
