package at.jku.isse.ecco.plugin.artifact.file;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.listener.ReadListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/* TODO: also include directories in this module? or move the file (or rather directory!) handling from modules to some sort of "super" module?
i would say we include the folder structure in our artifact tree! that way we can even store folder properties (in case it ever becomes relevant).
 */

public class FileReader implements ArtifactReader<Path, Set<Node>> {

	private final EntityFactory entityFactory;

	@Inject
	public FileReader(EntityFactory entityFactory) {
		com.google.common.base.Preconditions.checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public String getPluginId() {
		return FilePlugin.class.getName();
	}

	private static final String[] typeHierarchy = new String[]{};

	@Override
	public String[] getTypeHierarchy() {
		return typeHierarchy;
	}

	@Override
	public boolean canRead(Path path) {
		return (!Files.isDirectory(path) && Files.isRegularFile(path));
	}

	@Override
	public Set<Node> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node> read(Path base, Path[] input) {
		Set<Node> nodes = new HashSet<Node>();
		for (Path path : input) {
			try {
				Artifact<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
				Node pluginNode = this.entityFactory.createNode(pluginArtifact);
				nodes.add(pluginNode);

				FileArtifactData fileArtifactData = new FileArtifactData(base, path);
				Node fileNode = this.entityFactory.createNode(this.entityFactory.createArtifact(fileArtifactData));
				pluginNode.addChild(fileNode);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return nodes;
	}


	private Collection<ReadListener> listeners = new ArrayList<ReadListener>();

	@Override
	public void addListener(ReadListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		this.listeners.remove(listener);
	}

}
