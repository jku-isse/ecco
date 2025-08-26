package at.jku.isse.ecco.adapter.ds;

import at.jku.isse.designspace.variant.dto.*;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.ds.util.DsNodeFactory;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class DSReader implements ArtifactReader<Path, Set<Node.Op>> {

	public static final String PROPERTY_LINE_START = "LINE_START";
	public static final String PROPERTY_LINE_END = "LINE_END";

	private final EntityFactory entityFactory;
	private final DsNodeFactory dsNodeFactory;

	private final Gson gson;

	@Inject
	public DSReader(EntityFactory entityFactory) {
		checkNotNull(entityFactory);
		this.entityFactory = entityFactory;
		this.dsNodeFactory = new DsNodeFactory(entityFactory);

		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(PropertyDto.class, new PropertyDtoDeserializer());
		gsonBuilder.registerTypeAdapter(ValueDto.class, new ValueDtoDeserializer());
		this.gson = gsonBuilder.create();
	}

	@Override
	public String getPluginId() {
		return at.jku.isse.ecco.adapter.ds.DSPlugin.class.getName();
	}

	private static Map<Integer, String[]> prioritizedPatterns;

	static {
		prioritizedPatterns = new HashMap<>();
		prioritizedPatterns.put(1, new String[]{"**.desp"});
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
		for (Path path : input){
			nodes.add(this.readDsFile(base, path));
		}
		return nodes;
	}

	public Node.Op readDsFile(Path base, Path relativePath) {
		Path resolvedPath = base.resolve(relativePath);
		Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), relativePath));
		Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);

		byte[] bytes;
		try {
			bytes = Files.readAllBytes(resolvedPath);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		String folderJson = new String(bytes);
		FolderDto folderDto = gson.fromJson(folderJson, FolderDto.class);
		folderDto.setName("root");

		for (InstanceTypeDto instanceTypeDto : folderDto.getInstanceTypeDtos()){
			pluginNode.addChild(this.dsNodeFactory.createInstanceTypeNode(instanceTypeDto));
		}

		return pluginNode;
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
