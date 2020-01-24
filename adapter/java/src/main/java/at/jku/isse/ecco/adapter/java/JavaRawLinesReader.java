package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.java.data.ClassArtifactData;
import at.jku.isse.ecco.adapter.java.data.ImportArtifactData;
import at.jku.isse.ecco.adapter.java.data.LineArtifactData;
import at.jku.isse.ecco.adapter.java.data.MethodArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.google.inject.Inject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class JavaRawLinesReader implements ArtifactReader<Path, Set<Node.Op>> {

	private final EntityFactory entityFactory;

	@Inject
	public JavaRawLinesReader(EntityFactory entityFactory) {
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

			ArrayList<String> lines = new ArrayList<>();
			try (BufferedReader br = new BufferedReader(new FileReader(resolvedPath.toFile()))) {
				String line;
				int i = 0;
				while ((line = br.readLine()) != null) {
					lines.add(line);
					i++;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				CompilationUnit cu = JavaParser.parse(resolvedPath);

				String packageName = "";
				if (cu.getPackageDeclaration().isPresent())
					packageName = cu.getPackageDeclaration().get().getName().toString();


				for (TypeDeclaration<?> typeDeclaration : cu.getTypes()) {

					String className = typeDeclaration.getName().toString();
					Artifact.Op<ClassArtifactData> classArtifact = this.entityFactory.createArtifact(new ClassArtifactData(packageName + "." + className));
					Node.Op classNode = this.entityFactory.createOrderedNode(classArtifact);
					pluginNode.addChild(classNode);

					//add classChild from imports
//					Artifact.Op<AbstractArtifactData> importsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("IMPORTS"));
//					Node.Op importsGroupNode = this.entityFactory.createNode(importsGroupArtifact);
					for (ImportDeclaration importDeclaration : cu.getImports()) {
						String importName = "import " + importDeclaration.getName().asString();
						Artifact.Op<ImportArtifactData> importArtifact = this.entityFactory.createArtifact(new ImportArtifactData(importName));
						Node.Op importNode = this.entityFactory.createNode(importArtifact);
						classNode.addChild(importNode);
					}

					for (BodyDeclaration<?> node : typeDeclaration.getMembers()) {
						if (node instanceof ClassOrInterfaceDeclaration) {
							Artifact.Op<ClassArtifactData> nestedClassArtifact = this.entityFactory.createArtifact(new ClassArtifactData(packageName + "." + className + "." + ((ClassOrInterfaceDeclaration) node).getName().toString()));
							Node.Op nestedClassNode = this.entityFactory.createOrderedNode(nestedClassArtifact);
							classNode.addChild(nestedClassNode);
							addNestedClassChild(classNode, nestedClassNode, node, lines);
						} else {
							addClassChild(node, classNode, lines);
						}
					}

					//add classChild from Methods
					for (MethodDeclaration methodDeclaration : typeDeclaration.getMethods()) {
						String methodSignature = methodDeclaration.getName().toString() + "(";
						for (int j = 0; j < methodDeclaration.getParameters().size(); j++) {
							if (j < methodDeclaration.getParameters().size() - 1)
								methodSignature += methodDeclaration.getParameters().get(j).getType().toString() + ",";
							else
								methodSignature += methodDeclaration.getParameters().get(j).getType().toString();
						}
						methodSignature += ")";
						Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
						Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
						classNode.addChild(methodNode);
						addMethodChild(methodNode, methodDeclaration, lines);
					}
				}

			} catch (IOException e) {
				e.printStackTrace();
				throw new EccoException("Error parsing java file.", e);
			}

		}
		return nodes;
	}

	public void addNestedClassChild(Node.Op classNode, Node.Op nestedClassNode, com.github.javaparser.ast.Node node, ArrayList<String> lines) {

		for (BodyDeclaration<?> nestedClassBody : ((ClassOrInterfaceDeclaration) node).getMembers()) {
			if (nestedClassBody instanceof MethodDeclaration) {
				String methodSignature = ((MethodDeclaration) nestedClassBody).getName().toString() + "(";
				for (int j = 0; j < ((MethodDeclaration) nestedClassBody).getParameters().size(); j++) {
					if (j < ((MethodDeclaration) nestedClassBody).getParameters().size() - 1)
						methodSignature += ((MethodDeclaration) nestedClassBody).getParameters().get(j).getType().toString() + ",";
					else
						methodSignature += ((MethodDeclaration) nestedClassBody).getParameters().get(j).getType().toString();
				}
				methodSignature += ")";
				Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
				Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
				nestedClassNode.addChild(methodNode);
				addMethodChild(methodNode, ((MethodDeclaration) nestedClassBody), lines);
			} else if (nestedClassBody instanceof ClassOrInterfaceDeclaration) {
				Artifact.Op<ClassArtifactData> nestedClassArtifact = this.entityFactory.createArtifact(new ClassArtifactData(nestedClassNode.toString() + "." + (((ClassOrInterfaceDeclaration) nestedClassBody).getName().toString())));
				Node.Op nestedClassChildNode = this.entityFactory.createOrderedNode(nestedClassArtifact);
				nestedClassNode.addChild(nestedClassChildNode);
				addNestedClassChild(nestedClassNode, nestedClassChildNode, nestedClassBody, lines);
			} else {
				addClassChild(nestedClassBody, nestedClassNode, lines);
			}
		}
	}

	public void addClassChild(com.github.javaparser.ast.Node node, Node.Op classNode, ArrayList<String> lines) {
		//add classChild from enums
		if (node instanceof EnumDeclaration) {
			int beginLine = node.getRange().get().begin.line;
			int endLine = node.getRange().get().end.line;
			String line;
			int i = beginLine - 1;
			while (i <= endLine) {
				Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines.get(i)));
				Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
				classNode.addChild(lineNode);
				i++;
			}
		} else
			//add classChild from fields
			if (node instanceof FieldDeclaration) {
				// addFieldChild(node, classNode);
				int beginLine = node.getRange().get().begin.line;
				int endLine = node.getRange().get().end.line;
				String line;
				int i = beginLine - 1;
				while (i < endLine) {
					Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines.get(i)));
					Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
					classNode.addChild(lineNode);
					i++;
				}

			} else
				//add classChild from constructorMethod
				if (node instanceof ConstructorDeclaration) {
					String methodSignature = ((ConstructorDeclaration) node).getName().toString() + "(";
					for (int j = 0; j < ((ConstructorDeclaration) node).getParameters().size(); j++) {
						if (j < ((ConstructorDeclaration) node).getParameters().size() - 1)
							methodSignature += ((ConstructorDeclaration) node).getParameters().get(j).getType().toString() + ",";
						else
							methodSignature += ((ConstructorDeclaration) node).getParameters().get(j).getType().toString();
					}
					methodSignature += ")";
					Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
					Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
					classNode.addChild(methodNode);
					if (((ConstructorDeclaration) node).getBody().getStatements().isNonEmpty()) {
						int beginLine = node.getRange().get().begin.line;
						int endLine = node.getRange().get().end.line;
						String line;
						int i = beginLine;
						while (i < endLine - 1) {
							Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines.get(i)));
							Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
							methodNode.addChild(lineNode);
							i++;
						}
					}
				}
	}

	public void addMethodChild(Node.Op methodNode, MethodDeclaration methodDeclaration, ArrayList<String> lines) {
		//add classChild from Methods
		Optional<BlockStmt> block = methodDeclaration.getBody();
		Boolean hasAnyStatement = false;
		if (methodDeclaration.getBody().isPresent()) {
			int beginLine = methodDeclaration.getBody().get().getRange().get().begin.line;
			int endLine = methodDeclaration.getBody().get().getRange().get().end.line;
			String line;
			int i = beginLine;
			while (i < endLine - 1) {
				Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines.get(i)));
				Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
				methodNode.addChild(lineNode);
				i++;
			}

		}
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
