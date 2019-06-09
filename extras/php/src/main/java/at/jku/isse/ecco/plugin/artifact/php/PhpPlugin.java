package at.jku.isse.ecco.plugin.artifact.php;

import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import com.google.inject.Module;

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

}
