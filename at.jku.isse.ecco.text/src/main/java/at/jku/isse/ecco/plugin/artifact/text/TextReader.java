package at.jku.isse.ecco.plugin.artifact.text;

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
import java.util.*;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class TextReader implements ArtifactReader<Path, Set<Node.Op>> {

	private final EntityFactory entityFactory;

	@Inject
	public TextReader(EntityFactory entityFactory) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public String getPluginId() {
		return TextPlugin.class.getName();
	}

	private static final String[] typeHierarchy = new String[]{"text"};

	@Override
	public String[] getTypeHierarchy() {
		return typeHierarchy;
	}

	@Override
	public boolean canRead(Path path) {
		// TODO: actually check contents of file to see if it is a text file
		if (!Files.isDirectory(path) && Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".txt"))
			return true;
		else
			return false;
	}

	@Override
	public Set<Node.Op> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node.Op> read(Path base, Path[] input) {
		Set<Node.Op> nodes = new HashSet<>();
		for (Path path : input) {
			Path resolvedPath = base.resolve(path);
			Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
			Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
			nodes.add(pluginNode);

			try (Stream<String> lines = Files.lines(resolvedPath)) {
				Iterator<String> it = lines.iterator();
				while (it.hasNext()) {
					String line = it.next();
					Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(line));
					pluginNode.addChild(this.entityFactory.createNode(lineArtifact));
				}
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
