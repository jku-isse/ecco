package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class JavaReader implements ArtifactReader<Path, Set<Node.Op>> {

	private final EntityFactory entityFactory;

	@Inject
	public JavaReader(EntityFactory entityFactory) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public String getPluginId() {
		return JavaPlugin.class.getName();
	}

	private static Map<Integer, String[]> prioritizedPatterns;

	static {
		prioritizedPatterns = new HashMap<>();
		prioritizedPatterns.put(1, new String[]{"**.java"});
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
			Node.Op pluginNode = this.entityFactory.createNode(pluginArtifact);
			nodes.add(pluginNode);


			// TODO: user JavaParser to create remaining tree

			try {
				CompilationUnit cu = JavaParser.parse(resolvedPath);

				String packageName = "";
				if (cu.getPackageDeclaration().isPresent())
					packageName = cu.getPackageDeclaration().get().getName().toString();

				for (TypeDeclaration<?> typeDeclaration : cu.getTypes()) {

					String className = typeDeclaration.getName().toString();
					Artifact.Op<ClassArtifactData> classArtifact = this.entityFactory.createArtifact(new ClassArtifactData(packageName + "." + className));
					Node.Op classNode = this.entityFactory.createNode(classArtifact);
					pluginNode.addChild(classNode);

					// TODO: add contents of class to tree

					System.out.println(typeDeclaration.getMethods().get(0).getSignature().toString());
					System.out.println(typeDeclaration.getMethods().get(0).getBody().get().toString());
					System.out.println(Arrays.asList(typeDeclaration.getMethods().get(0).getBody().get().toString().split("\\r?\\n")));


				}

			} catch (IOException e) {
				e.printStackTrace();
				throw new EccoException("Error parsing java file.", e);
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
