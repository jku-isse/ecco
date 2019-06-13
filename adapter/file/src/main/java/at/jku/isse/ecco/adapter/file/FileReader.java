package at.jku.isse.ecco.adapter.file;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/* TODO: also include directories in this module? or move the file (or rather directory!) handling from modules to some sort of "super" module?
i would say we include the folder structure in our artifact tree! that way we can even store folder properties (in case it ever becomes relevant).
 */

public class FileReader implements ArtifactReader<Path, Set<Node.Op>> {

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

	private static Map<Integer, String[]> prioritizedPatterns;

	static {
		prioritizedPatterns = new HashMap<>();
		prioritizedPatterns.put(0, new String[]{"**"});
	}

	@Override
	public Map<Integer, String[]> getPrioritizedPatterns() {
		return Collections.unmodifiableMap(prioritizedPatterns);
	}

	@Override
	public Set<Node.Op> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node.Op> read(Path base, Path[] input) {
		Set<Node.Op> nodes = new HashSet<>();
		for (Path path : input) {
			try {
				Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
				Node.Op pluginNode = this.entityFactory.createNode(pluginArtifact);
				nodes.add(pluginNode);

				FileArtifactData fileArtifactData = new FileArtifactData(base, path);
				Node.Op fileNode = this.entityFactory.createNode(this.entityFactory.createArtifact(fileArtifactData));
				pluginNode.addChild(fileNode);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return nodes;
	}


	private Collection<ReadListener> listeners = new ArrayList<>();

	@Override
	public void addListener(ReadListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		this.listeners.remove(listener);
	}

}
