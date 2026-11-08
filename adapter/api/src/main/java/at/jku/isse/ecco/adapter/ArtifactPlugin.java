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

	/**
	 * What this plugin records in a NEW repository's settings ({@code .ecco/.settings}), for its
	 * adapter to read back on every later commit and checkout - a choice that decides how files are
	 * read into trees, and so must not change over a repository's life. A repository created before
	 * a setting existed has none, and its absence must mean the behaviour from before.
	 *
	 * @return setting names (prefixed with the plugin's own name) and their values
	 */
	public java.util.Map<String, String> newRepositorySettings() {
		return java.util.Map.of();
	}

	public static ArtifactPlugin[] getArtifactPlugins() {
		final ServiceLoader<ArtifactPlugin> loader = ServiceLoader.load(ArtifactPlugin.class);

		List<ArtifactPlugin> plugins = new ArrayList<>();

		for (final ArtifactPlugin plugin : loader) {
			plugins.add(plugin);
		}

		return plugins.toArray(new ArtifactPlugin[0]);
	}

}
