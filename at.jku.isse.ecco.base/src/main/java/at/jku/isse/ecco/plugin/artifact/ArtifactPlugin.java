package at.jku.isse.ecco.plugin.artifact;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Module;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

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


	// TODO: these are not needed anymore!

	public abstract ArtifactReader<Path, Set<Node>> createReader(final EntityFactory entityFactory); // TODO: this should be abstract static!

	public abstract ArtifactWriter<Set<Node>, Path> createWriter(); // TODO: this should be abstract static!

}
