package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class TextReader implements ArtifactReader<Path, Set<Node.Op>> {

	public static final String PROPERTY_LINE_START = "LINE_START";
	public static final String PROPERTY_LINE_END = "LINE_END";

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

	private static Map<Integer, String[]> prioritizedPatterns;

	static {
		prioritizedPatterns = new HashMap<>();
		prioritizedPatterns.put(1, new String[]{"**.txt", "**.md", "**.xml", "**.html", "**.css", "**.js", "**.java", "**.c", "**.h", "**.cpp", "**.hpp"});
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
			Path resolvedPath = base.resolve(path);
			Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
			Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
			nodes.add(pluginNode);

			try (BufferedReader br = new BufferedReader(new FileReader(resolvedPath.toFile()))) {
				String line;
				int i = 0;
				while ((line = br.readLine()) != null) {
					i++;
					Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(line));
					Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
					lineNode.putProperty(PROPERTY_LINE_START, i);
					lineNode.putProperty(PROPERTY_LINE_END, i);
					pluginNode.addChild(lineNode);
				}
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
