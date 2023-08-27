package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.typescript.data.IfBlockArtifactData;
import at.jku.isse.ecco.adapter.typescript.data.LineArtifactData;
import at.jku.isse.ecco.adapter.typescript.data.VariableAssignmentData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.caoccao.javet.utils.JavetOSUtils;
import com.google.inject.Inject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class TypeScriptReader implements ArtifactReader<Path, Set<Node.Op>> {

	public static final String PROPERTY_LINE_START = "LINE_START";
	public static final String PROPERTY_LINE_END = "LINE_END";

	private final EntityFactory entityFactory;

	@Inject
	public TypeScriptReader(EntityFactory entityFactory) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public String getPluginId() {
		return TypeScriptPlugin.class.getName();
	}

	private static Map<Integer, String[]> prioritizedPatterns;

	static {
		prioritizedPatterns = new HashMap<>();
		prioritizedPatterns.put(1, new String[]{"**.ts"});//, "**.c", "**.h", "**.cpp", "**.hpp"});
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
		System.out.println("working dir");
		System.out.println(JavetOSUtils.WORKING_DIRECTORY);
		Set<Node.Op> nodes = new HashSet<>();
		for (Path path : input) {
			Path resolvedPath = base.resolve(path);
			Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
			Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
			nodes.add(pluginNode);
			try {
				HashMap<String,Object> br = new TypeScriptParser().parse(resolvedPath);
				int i = 0;
				ArrayList<HashMap<String,Object>> stat = (ArrayList<HashMap<String, Object>>) br.get("statements");
				for (HashMap<String, Object> stringObjectHashMap : stat) {
					pluginNode.addChild(makeNode(stringObjectHashMap));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
		return nodes;
	}

	private Node.Op makeNode(HashMap<String,Object> child){
		Node.Op node;
		String kind = (String) child.get("kind");
		switch (kind){
			case "FirstStatement" :
				Artifact.Op<VariableAssignmentData> op = this.entityFactory.createArtifact(new VariableAssignmentData((String)child.get("nodeText")));
				node = this.entityFactory.createNode(op);
				break;
			case "IfStatement" :
				Artifact.Op<IfBlockArtifactData> iff = this.entityFactory.createArtifact(new IfBlockArtifactData((String)child.get("nodeText")));
				node = this.entityFactory.createNode(iff);
				HashMap<String,Object> then = (HashMap<String, Object>) child.get("thenStatement");
				node.addChild(makeNode(then));
				HashMap<String,Object> elseBlock = (HashMap<String, Object>) child.get("elseStatement");
				if (elseBlock != null){
					node.addChild(makeNode(elseBlock));
				}
				break;
			default:
				Artifact.Op<LineArtifactData> line = this.entityFactory.createArtifact(new LineArtifactData((String)child.get("nodeText")));
				node = this.entityFactory.createNode(line);
				break;
		}
		return node;
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
