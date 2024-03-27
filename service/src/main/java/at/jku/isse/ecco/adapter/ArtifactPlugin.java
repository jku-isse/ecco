package at.jku.isse.ecco.adapter;

import com.google.inject.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public abstract class ArtifactPlugin {

	/**
	 * Must always return the plugin class name:
	 * ArtifactPlugin.class.getName().
	 *
	 * @return The plugin id string.
	 */
	public abstract String getPluginId();

	public abstract Module getModule();

	public abstract String getName(); // should be abstract static

	public abstract String getDescription(); // should be abstract static

	public static ArtifactPlugin[] getArtifactPlugins() {
		final ServiceLoader<ArtifactPlugin> loader = ServiceLoader.load(ArtifactPlugin.class);

		List<ArtifactPlugin> plugins = new ArrayList<>();

		for (final ArtifactPlugin plugin : loader) {
			plugins.add(plugin);
		}

		return plugins.toArray(new ArtifactPlugin[0]);
	}

}
