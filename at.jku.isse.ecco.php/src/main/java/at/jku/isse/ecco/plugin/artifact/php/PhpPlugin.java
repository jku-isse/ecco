package at.jku.isse.ecco.plugin.artifact.php;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Module;

import java.nio.file.Path;
import java.util.Set;

/**
 * @author Timea Kovacs
 */
public class PhpPlugin extends ArtifactPlugin {

//	private static final String[] fileTypes = new String[]{"php", "xml"};

	private PhpModule module = new PhpModule();

//	public String[] getFileTypes() {
//		return fileTypes;
//	}

	@Override
	public String getPluginId() {
		return PhpPlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "PhpArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "Php Artifact Plugin";
	}

	@Override
	public ArtifactReader<Path, Set<Node>> createReader(final EntityFactory entityFactory) {
		return new PhpReader(entityFactory);
	}

	@Override
	public ArtifactWriter<Set<Node>, Path> createWriter() {
		return new PhpWriter();
	}

}
