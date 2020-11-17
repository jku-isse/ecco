package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.java.data.*;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
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
		prioritizedPatterns.put(Integer.MAX_VALUE, new String[]{"**.java"});
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

					//add classChild from imports
					for(ImportDeclaration importDeclaration : cu.getImports()){
						String importName = importDeclaration.getName().asString();
						Artifact.Op<ImportArtifactData> importsArtifact = this.entityFactory.createArtifact(new ImportArtifactData(importName));
						Node.Op importNode = this.entityFactory.createNode(importsArtifact);
						classNode.addChild(importNode);
					}

					//add classChild from fields
					for(FieldDeclaration fieldDeclaration : typeDeclaration.getFields()) {
						String fieldOfClass = fieldDeclaration.removeComment().toString();
						Artifact.Op<FieldArtifactData> fieldArtifact = this.entityFactory.createArtifact(new FieldArtifactData(fieldOfClass));
						Node.Op fieldNode = this.entityFactory.createNode(fieldArtifact);
						classNode.addChild(fieldNode);
					}
					//add classChild from Methods
					for(MethodDeclaration methodDeclaration : typeDeclaration.getMethods()){
						String methodSignature = methodDeclaration.getSignature().toString();
						Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
						Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
						classNode.addChild(methodNode);
						Optional<BlockStmt> block = methodDeclaration.getBody();
						Boolean hasAnyStatement = false;
						if(methodDeclaration.getBody().isPresent()) {


							String[] lines = methodDeclaration.getBody().get().removeComment().toString().split("\r?\n");
							for (String line : lines) {
								Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(line.trim()));
								Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
								methodNode.addChild(lineNode);
							}

							/*
							NodeList<Statement> statements = block.get().getStatements();
							for (Statement tmp : statements) {
								tmp.getChildNodes().get(0).getChildNodes().get(0);
								String line = tmp.removeComment().toString();
								Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(line));
								Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
								methodNode.addChild(lineNode);
							}*/


						}
					}

					//print tree
					//System.out.println("\n Class: "+classNode.toString());
					//for (int i=0; i<classNode.getChildren().size(); i++){ //imports, fields and methods declaration
					//	System.out.println("\n Class child: "+classNode.getChildren().get(i));
					//	if(classNode.getChildren().get(i).getChildren().size() > 0){ //methods statements and methods fields declaration
					//		for(int j=0; j<classNode.getChildren().get(i).getChildren().size(); j++){
					//			System.out.println("\n Method child: "+classNode.getChildren().get(i).getChildren().get(j));
					//			for(int k=0; k<classNode.getChildren().get(i).getChildren().get(j).getChildren().size(); k++){
					//				System.out.println("\n Statement: "+classNode.getChildren().get(i).getChildren().get(j).getChildren().get(k));
					//			}
					//		}
					//	}
					//}

					//System.out.println(typeDeclaration.getMethods().get(0).getBody().get().toString());
					//System.out.println(Arrays.asList(typeDeclaration.getMethods().get(0).getBody().get().toString().split("\\r?\\n")));

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
