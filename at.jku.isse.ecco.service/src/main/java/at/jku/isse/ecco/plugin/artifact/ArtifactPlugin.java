package at.jku.isse.ecco.plugin.artifact;

import com.google.inject.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public abstract class ArtifactPlugin {

	public abstract String getPluginId();

	public abstract Module getModule();

	public abstract String getName(); // should be abstract static

	public abstract String getDescription(); // should be abstract static

	public static ArtifactPlugin[] getArtifactPlugins() {
		final ServiceLoader<ArtifactPlugin> loader = ServiceLoader.load(ArtifactPlugin.class);

		List<ArtifactPlugin> plugins = new ArrayList<ArtifactPlugin>();

		for (final ArtifactPlugin plugin : loader) {
			plugins.add(plugin);
		}

		return plugins.toArray(new ArtifactPlugin[plugins.size()]);
	}

}
