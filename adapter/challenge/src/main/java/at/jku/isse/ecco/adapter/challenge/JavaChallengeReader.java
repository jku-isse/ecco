package at.jku.isse.ecco.adapter.challenge;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.challenge.data.*;
import at.jku.isse.ecco.featuretrace.parser.VevosConditionHandler;
import at.jku.isse.ecco.adapter.dispatch.DispatchWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.parser.VevosCondition;
import at.jku.isse.ecco.featuretrace.parser.VevosFileConditionContainer;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;
import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class JavaChallengeReader implements ArtifactReader<Path, Set<Node.Op>> {

	protected static final Logger LOGGER = Logger.getLogger(DispatchWriter.class.getName());

	private final EntityFactory entityFactory;

	private Collection<ReadListener> listeners = new ArrayList<>();

	@Inject
	public JavaChallengeReader(EntityFactory entityFactory) {
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
		VevosConditionHandler vevosConditionHandler = new VevosConditionHandler(base);
		Set<Node.Op> nodes = new HashSet<>();

		long totalJavaParserTime = 0;

		for (Path path : input) {
			VevosFileConditionContainer fileConditionContainer = vevosConditionHandler.getFileSpecificPresenceConditions(path);

			Path resolvedPath = base.resolve(path);

			// create plugin artifact/node
			Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
			Node.Op pluginNode = this.entityFactory.createNode(pluginArtifact);
			nodes.add(pluginNode);

			try {
				// read raw file contents
				String fileContent = new String(Files.readAllBytes(resolvedPath), StandardCharsets.UTF_8);
				String[] lines = fileContent.split("\\r?\\n");

				long localStartTime = System.currentTimeMillis();
				CompilationUnit cu = StaticJavaParser.parse(fileContent);
				totalJavaParserTime += (System.currentTimeMillis() - localStartTime);

				// package name
				String packageName = "";
				if (cu.getPackageDeclaration().isPresent())
					packageName = cu.getPackageDeclaration().get().getName().toString();

				for (TypeDeclaration<?> typeDeclaration : cu.getTypes()) {
					// create class artifact/node
					String className = typeDeclaration.getName().toString();
					Artifact.Op<ClassArtifactData> classArtifact = this.entityFactory.createArtifact(new ClassArtifactData(packageName + "." + className));

					Location location = new Location(typeDeclaration.getRange().get().begin.line,
							typeDeclaration.getRange().get().begin.line, path);
					Node.Op classNode = this.createNodeWithLocation(classArtifact, location);
					pluginNode.addChild(classNode);
					this.checkForFeatureTrace(typeDeclaration, fileConditionContainer, classNode);

					// imports
					Artifact.Op<AbstractArtifactData> importsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("IMPORTS"));
					Node.Op importsGroupNode = this.entityFactory.createNode(importsGroupArtifact);
					classNode.addChild(importsGroupNode);
					for (ImportDeclaration importDeclaration : cu.getImports()) {
						String importName = "import " + importDeclaration.getName().asString();
						Artifact.Op<ImportArtifactData> importArtifact = this.entityFactory.createArtifact(new ImportArtifactData(importName));

						location = new Location(importDeclaration.getRange().get().begin.line,
								importDeclaration.getRange().get().begin.line, path);
						Node.Op importNode = this.createNodeWithLocation(importArtifact, location);

						importsGroupNode.addChild(importNode);
						this.checkForFeatureTrace(importDeclaration, fileConditionContainer, importNode);
					}
					ArrayList<String> methods =  new ArrayList<>();
					this.addClassChildren(typeDeclaration, classNode, lines, methods, fileConditionContainer, path);
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new EccoException("Error parsing java file.", e);
			}
		}

		LOGGER.fine(JavaParser.class + ".parse(): " + totalJavaParserTime + "ms");
		return nodes;
	}

	public Set<Node.Op> read(Path base, Path[] input, ArrayList<String> methods) {
		return null;
	}

	private void addClassChildren(TypeDeclaration<?> typeDeclaration,
								  Node.Op classNode,
								  String[] lines,
								  ArrayList<String> methods,
								  VevosFileConditionContainer fileConditionContainer,
								  Path path) {

		// create methods artifact/node
		Artifact.Op<AbstractArtifactData> methodsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("METHODS"));
		Node.Op methodsGroupNode = this.entityFactory.createNode(methodsGroupArtifact);
		classNode.addChild(methodsGroupNode);
		// create fields artifact/node
		Artifact.Op<AbstractArtifactData> fieldsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("FIELDS"));
		Node.Op fieldsGroupNode = this.entityFactory.createOrderedNode(fieldsGroupArtifact);
		classNode.addChild(fieldsGroupNode);
		// create enums artifact/node
		Artifact.Op<AbstractArtifactData> enumsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("ENUMS"));
		Node.Op enumsGroupNode = this.entityFactory.createOrderedNode(enumsGroupArtifact);
		classNode.addChild(enumsGroupNode);
		for (BodyDeclaration<?> node : typeDeclaration.getMembers()) {
			// nested classes/interfaces
			if (node instanceof ClassOrInterfaceDeclaration) {
				Artifact.Op<ClassArtifactData> nestedClassArtifact = this.entityFactory.createArtifact(new ClassArtifactData(classNode.toString() + "." + ((ClassOrInterfaceDeclaration) node).getName().toString()));

				Location location = new Location(typeDeclaration.getRange().get().begin.line,
						typeDeclaration.getRange().get().begin.line, path);
				Node.Op nestedClassNode = this.createNodeWithLocation(nestedClassArtifact, location);

				classNode.addChild(nestedClassNode);
				this.checkForFeatureTrace(node, fileConditionContainer, nestedClassNode);
				addClassChildren((ClassOrInterfaceDeclaration) node, nestedClassNode, lines, methods, fileConditionContainer, path);
			}
			// enumerations
			else if (node instanceof EnumDeclaration) {
				int beginLine = node.getRange().get().begin.line;
				int endLine = node.getRange().get().end.line;
				int i = beginLine - 1;
				while (i <= endLine) {
					String trimmedLine = lines[i].trim();
					if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
						Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
						Location location = new Location(beginLine, endLine, path);
						Node.Op lineNode = this.createNodeWithLocation(lineArtifact, location);
						enumsGroupNode.addChild(lineNode);
						this.checkForFeatureTrace(i + 1, fileConditionContainer, lineNode);
					}
					i++;
				}
			}
			// fields
			else if (node instanceof FieldDeclaration) {
				int beginLine = node.getRange().get().begin.line;
				int endLine = node.getRange().get().end.line;
				String line;
				int i = beginLine - 1;
				while (i < endLine) {
					int lineNumber = i + 1;
					String trimmedLine = lines[i].trim();
					if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
						Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
						Location location = new Location(lineNumber, lineNumber, path);
						Node.Op lineNode = this.createNodeWithLocation(lineArtifact, location);
						fieldsGroupNode.addChild(lineNode);
						this.checkForFeatureTrace(i + 1, fileConditionContainer, lineNode);
					}
					i++;
				}
			}
			// constructors
			else if (node instanceof ConstructorDeclaration) {
				String methodSignature = ((ConstructorDeclaration) node).getName().toString() + "(" +
						((ConstructorDeclaration) node).getParameters().stream().map(parameter -> parameter.getType().toString()).collect(Collectors.joining(",")) +
						")";
				methods.add(classNode.getArtifact() + " " + methodSignature);
				Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
				Location constructorLocation = new Location(node.getRange().get().begin.line, node.getRange().get().end.line, path);
				Node.Op methodNode = this.createOrderedNodeWithLocation(methodArtifact, constructorLocation);
				this.checkForFeatureTrace(node, fileConditionContainer, methodNode);

				methodsGroupNode.addChild(methodNode);
				if (((ConstructorDeclaration) node).getBody().getStatements().isNonEmpty()) {
					int beginLine = node.getRange().get().begin.line;
					int endLine = node.getRange().get().end.line;
					int i = beginLine;
					while (i < endLine - 1) {
						int lineNumber = i + 1;
						String trimmedLine = lines[i].trim();
						if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
							Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
							Location location = new Location(lineNumber, lineNumber, path);
							Node.Op lineNode = this.createNodeWithLocation(lineArtifact, location);
							methodNode.addChild(lineNode);
							this.checkForFeatureTrace(i + 1, fileConditionContainer, lineNode);
						}
						i++;
					}
				}
			}
		}

		// methods
		for (MethodDeclaration methodDeclaration : typeDeclaration.getMethods()) {
			String methodSignature = methodDeclaration.getName().toString() + "(" +
					methodDeclaration.getParameters().stream().map(parameter -> parameter.getType().toString()).collect(Collectors.joining(",")) +
					")";
			methods.add(classNode.getArtifact() + " " + methodSignature);
			Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
			Location methodLocation = new Location(methodDeclaration.getRange().get().begin.line, methodDeclaration.getRange().get().end.line, path);
			Node.Op methodNode = this.createOrderedNodeWithLocation(methodArtifact, methodLocation);
			this.checkForFeatureTrace(methodDeclaration, fileConditionContainer, methodNode);

			methodsGroupNode.addChild(methodNode);
			addMethodChildren(methodDeclaration, methodNode, lines, fileConditionContainer, path);
		}
	}

	private void addMethodChildren(MethodDeclaration methodDeclaration, Node.Op methodNode, String[] lines,
								   VevosFileConditionContainer fileConditionContainer, Path path) {
		// lines inside method
		if (methodDeclaration.getBody().isPresent()) {
			int beginLine = methodDeclaration.getBody().get().getRange().get().begin.line;
			int endLine = methodDeclaration.getBody().get().getRange().get().end.line;
			int i = beginLine;
			while (i < endLine - 1) {
				int lineNumber = i + 1;
				String trimmedLine = lines[i].trim();
				if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
					Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
					Location location = new Location(lineNumber, lineNumber, path);
					Node.Op lineNode = this.createNodeWithLocation(lineArtifact, location);
					methodNode.addChild(lineNode);
					this.checkForFeatureTrace(i + 1, fileConditionContainer, lineNode);
				}
				i++;
			}
		}
	}

	private Node.Op createNodeWithLocation(Artifact.Op artifact, Location location){
		Node.Op node = this.entityFactory.createNode(artifact);
		node.setLocation(location);
		return node;
	}

	private Node.Op createOrderedNodeWithLocation(Artifact.Op artifact, Location location){
		Node.Op node = this.entityFactory.createOrderedNode(artifact);
		node.setLocation(location);
		return node;
	}

	private void checkForFeatureTrace(com.github.javaparser.ast.Node astNode, VevosFileConditionContainer fileConditionContainer, Node.Op node){
		if (fileConditionContainer == null){ return; }
		int startLine = astNode.getRange().get().begin.line;
		int endLine = astNode.getRange().get().end.line;
		Collection<VevosCondition> matchingConditions = fileConditionContainer.getMatchingPresenceConditions(startLine, endLine);
		for(VevosCondition condition : matchingConditions){
			FeatureTrace nodeTrace = node.getFeatureTrace();
			nodeTrace.buildUserConditionConjunction(condition.getConditionString());
		}
	}

	private void checkForFeatureTrace(int lineNumber, VevosFileConditionContainer fileConditionContainer, Node.Op node){
		if (fileConditionContainer == null){ return; }
		Collection<VevosCondition> matchingConditions = fileConditionContainer.getMatchingPresenceConditions(lineNumber, lineNumber);
		for(VevosCondition condition : matchingConditions){
			FeatureTrace nodeTrace = node.getFeatureTrace();
			nodeTrace.buildUserConditionConjunction(condition.getConditionString());
		}
	}

	@Override
	public void addListener(ReadListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		this.listeners.remove(listener);
	}
}
