package at.jku.isse.ecco.importer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

import org.apache.commons.io.FileUtils;

import com.bpodgursky.jbool_expressions.Expression;
import com.bpodgursky.jbool_expressions.parsers.ExprParser;
import com.bpodgursky.jbool_expressions.rules.RuleSet;

import at.jku.isse.ecco.adapter.dispatch.DirectoryArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.text.TextPlugin;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.repository.Repository.Op;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.core.MemAssociation;
import at.jku.isse.ecco.storage.mem.module.MemModule;
import at.jku.isse.ecco.storage.mem.module.MemModuleRevision;
import at.jku.isse.ecco.storage.mem.tree.MemNode;
import at.jku.isse.ecco.storage.mem.tree.MemRootNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.Node.NodeVisitor;

import static at.jku.isse.ecco.PreprocessorSyntax.IF;
import static at.jku.isse.ecco.PreprocessorSyntax.ELSE;
import static at.jku.isse.ecco.PreprocessorSyntax.ENDIF;


public class TraceImporterV2 {
	private final Map<String, Association.Op> map = new HashMap<>();
	private final Stack<Association> actualAssociations = new Stack<>();
	
	private final Repository.Op repository;
	private final Path fromPath;
	private final Path repPath;
	private final String fileExtensionRegex;
	private final LineImporter lineImporter;
	private Association.Op baseAssociation;
	
	public TraceImporterV2(Repository.Op repository, Path fromPath, Path repPath, String fileExtensionRegex,
			LineImporter lineImporter) {
		this.repository = repository;
		this.fromPath = fromPath;
		this.repPath = repPath;
		this.fileExtensionRegex = fileExtensionRegex;
		this.lineImporter = lineImporter;
	}

	public void importFolder() {
		Map<Path, List<Node.Op>> fileList = createBaseAssociation();
		fileList.forEach((file, nodeList) -> {
			importFile(file, nodeList);
		});
		
		map.forEach((key, value) -> repository.addAssociation(value));
		// map.forEach((key, value) -> System.out.println(value.computeCondition().getPreprocessorConditionString())); //TODO remove
		System.out.println("end");
	}

	private Map<Path, List<Node.Op>> createBaseAssociation() {
		this.baseAssociation = new MemAssociation();
		MemRootNode rootNode = new MemRootNode();
		baseAssociation.setRootNode(rootNode);
		rootNode.setContainingAssociation(baseAssociation);
		Node.Op rootDirNode = new MemNode(new MemArtifact<>(new DirectoryArtifactData(Paths.get(""))));
		rootDirNode.setUnique(true);
		rootDirNode.getArtifact().setContainingNode(rootDirNode);		
		rootNode.addChild(rootDirNode);
		setCondition("base", baseAssociation);
		map.put("base", baseAssociation);
		
		Map<Path, List<Node.Op>> fileList = new HashMap<>();
		try {
			Files.walk(fromPath)
					.filter(file -> file.toString().endsWith(fileExtensionRegex))
					.forEach(file -> {
						Path relPath = fromPath.relativize(file);
						List<Node.Op> nodeList = new ArrayList<>(); 
						fileList.put(file, nodeList);
						Node.Op dirNode = rootDirNode;
						if (relPath.getParent() != null) {
							Path resolvedPath = Paths.get("");
							for (Path path : relPath.getParent()) {
								resolvedPath = resolvedPath.resolve(path);
								Node.Op newDirNode = getDirNode(resolvedPath, baseAssociation);
								if (newDirNode == null) {
									MemArtifact<DirectoryArtifactData> dirArtifact = new MemArtifact<>(
											new DirectoryArtifactData(resolvedPath));
									newDirNode = new MemNode(dirArtifact);
									newDirNode.setUnique(true);
									dirArtifact.setContainingNode(newDirNode);
									dirNode.addChild(newDirNode);
								}
								nodeList.add(newDirNode);
								dirNode = newDirNode;
							}
						}
						MemArtifact<PluginArtifactData> pluginArtifact = new MemArtifact<>(
								new PluginArtifactData(TextPlugin.class.getName(), relPath), true);
						Node.Op pluginNode = new MemNode(pluginArtifact);
						nodeList.add(pluginNode);
						pluginNode.setUnique(true);
						pluginArtifact.setContainingNode(pluginNode);
						pluginArtifact.setSequenceGraph(pluginArtifact.createSequenceGraph());
						dirNode.addChild(pluginNode);
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		return fileList;
	}

	private void importFile(Path file, List<Node.Op> nodeList) {
		PartialOrderGraph.Op pog = nodeList.get(nodeList.size() - 1).getArtifact().getSequenceGraph();
		Stack<String> actualCondition = new Stack<>();
		Stack<Boolean> elseIfCounter = new Stack<>();
		Node.Op[] actualPluginNode = new MemNode[1];
		actualPluginNode[0] = nodeList.get((nodeList.size() - 1));
		Path relPath = fromPath.relativize(file);

		try {
			Files.lines(file)
					.forEach(line -> {
						if (line.matches("^((\\s)*" + IF + " (.)*)")) {
							elseIfCounter.push(false);
							actualCondition.push(line.replaceFirst("(\\s)*" + IF + " ", ""));
							String dnfCondition = parseCondition(actualCondition.peek());
							//System.out.println(dnfCondition);
							actualPluginNode[0] = startNewBlock(dnfCondition, relPath, repository, nodeList);
						} else if (line.matches("^((\\s)*" + ELSE + ")")) {
							actualPluginNode[0] = endBlock(relPath);
							String dnfCondition = parseCondition("!(" + actualCondition.peek() + ")");
							actualPluginNode[0] = startNewBlock(dnfCondition, relPath, repository, nodeList);
						} else if (line.matches("^((\\s)*" + ENDIF + ")")) {
							if(elseIfCounter.pop()) {
								lineImporter.importLine(line, actualPluginNode[0]);
							} else {
								actualCondition.pop();
								actualPluginNode[0] = endBlock(relPath);
							}
						} else {
							if(line.matches("^((\\s)*#ifdef (.)*)") || line.matches("^((\\s)*#ifndef (.)*)")) {
								elseIfCounter.push(true); 
							}
							lineImporter.importLine(line, actualPluginNode[0]);
							System.out.println(actualPluginNode[0].getContainingAssociation().computeCondition().getPreprocessorConditionString());
						}
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		exportPOG(pog, repPath, relPath);
	}

	private String parseCondition(String condition) {
//		StringBuilder sb = new StringBuilder();
//		for(String andPart : condition.split("&&")) {
//			for(String orPart : andPart.split("||")) {
//				sb.append("\"");
//				sb.append(orPart);
//				sb.append("\"");
//				sb.append("||");
//			}
//			sb.append("&&");
//		}
//		condition = "\"" + condition + "\""; // TODO split 
		if (!actualAssociations.isEmpty())
			condition = "(" + condition + ")&&"
					+ actualAssociations.peek().computeCondition().getPreprocessorConditionString();
		else condition = "(" + condition + ")&& base"; 
		condition = condition.replace("||", "|");
		condition = condition.replace("&&", "&");
		Expression<String> expr = ExprParser.parse(condition);
		expr = RuleSet.toDNF(expr);
		return expr.toString();
	}

	private List<Set<Feature>> addNewFeatures(String condition) {
		List<Set<Feature>> featureLists = new ArrayList<>(2);
		featureLists.add(new HashSet<>());
		featureLists.add(new HashSet<>());
		String[] features = condition.split("&");
		Arrays.stream(features).filter(f -> f.length() > 0).forEach(f -> {
			f = f.replaceAll("\\(|\\)| ", "");
			if (f.startsWith("!")) {
				f = f.substring(1);
				Feature feature = repository.addFeature("" + f.hashCode(), f);
				featureLists.get(1).add((feature == null ? repository.getFeature("" + f.hashCode()) : feature));
			} else {
				Feature feature = repository.addFeature("" + f.hashCode(), f);
				featureLists.get(0).add((feature == null ? repository.getFeature("" + f.hashCode()) : feature));
			}
		});
		return featureLists;
	}

	private Node.Op startNewBlock(String condition, Path file, Op repository, List<Node.Op> nodeList) {
		Association.Op association = map.get(condition);
		Node.Op lastNode, actualNode;
		if(association == null) {
			association = new MemAssociation();
			association.setId(UUID.randomUUID().toString());
			MemRootNode rootNode = new MemRootNode();
			association.setRootNode(rootNode);
			lastNode = new MemNode(
					(Artifact.Op<?>) baseAssociation.getRootNode().getChildren().get(0).getArtifact());
			lastNode.setUnique(false);
			rootNode.setContainingAssociation(association);
			rootNode.addChild(lastNode);
			map.put(condition, association);
			//set condition
			setCondition(condition, association);
		} else lastNode = association.getRootNode().getChildren().get(0);
		
		for(Node.Op node: nodeList) {
			actualNode = lastNode.getChildren().stream()
											   .filter((child) -> child.getArtifact() == node.getArtifact())
											   .findAny()
											   .orElse(null);
			if(actualNode == null) {
				actualNode = new MemNode(node.getArtifact());
				actualNode.setUnique(false);
			}
			lastNode.addChild(actualNode);
			lastNode = actualNode;
		}

		actualAssociations.push(association);
		return lastNode;
	}

	private void setCondition(String condition, Association.Op association) {
		//condition = condition.substring(1, condition.length() - 1);
		String[] conjunctions = condition.split("\\|");
		for (String conjunction : conjunctions) {
			List<Set<Feature>> featureLists = addNewFeatures(conjunction);
			MemModule module = new MemModule(featureLists.get(0).stream().toArray(Feature[]::new),
					featureLists.get(1).stream().toArray(Feature[]::new));
			FeatureRevision[] posFeatureRevisions = featureLists.get(0).stream().map(feature -> {
				return feature.getLatestRevision() != null ? feature.getLatestRevision()
						: feature.addRevision(UUID.randomUUID().toString());
			}).toArray(FeatureRevision[]::new);

			module.incCount();
			MemModuleRevision moduleRevision = module.addRevision(posFeatureRevisions,
					featureLists.get(1).stream().toArray(Feature[]::new));
			moduleRevision.incCount();
			association.addObservation(moduleRevision);
		}
	}

	private Node.Op endBlock(Path file) {
		actualAssociations.pop();
		if (actualAssociations.isEmpty())
			return getPluginNode(file, baseAssociation);
		else
			return getPluginNode(file, actualAssociations.peek());
	}

	private static void exportPOG(PartialOrderGraph pog, Path repPath, Path relPath) {
		Path file = repPath.resolve(relPath);
		try {
			FileUtils.forceMkdirParent(new File(file.toString()));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try(BufferedWriter bufferedWriter = Files.newBufferedWriter(file)){	
			pog.getHead().traverse(new PartialOrderGraph.Node.NodeVisitor() {				
				@Override
				public void visit(PartialOrderGraph.Node node) {
					try {
						if(node.getArtifact() != null)
							bufferedWriter.write(node.getArtifact().toString() + "\n");
					} catch (IOException e) {
						e.printStackTrace();
					}
					
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Searches plug-in node in given association witch matches the given path.
	 */
	private MemNode getPluginNode(Path file, Association association) {
		List<MemNode> result = new ArrayList<>(1);
		association.getRootNode().traverse(new NodeVisitor() {
			@Override
			public void visit(Node node) {
				try {
					if (node.getArtifact() != null) {
						PluginArtifactData data = (PluginArtifactData) node.getArtifact().getData();
						if (data.getPath().equals(file)) {
							result.add((MemNode) node);
						}
					}
				} catch (ClassCastException e) {
					// ignor
				}
			}
		});
		return (result.isEmpty()) ? null : result.get(0);
	}
	
	/**
	 * Searches directory node in given association witch matches the given
	 * path.
	 */
	private MemNode getDirNode(Path path, Association association) {
		List<MemNode> result = new ArrayList<>(1);
		association.getRootNode().traverse(new NodeVisitor() {
			@Override
			public void visit(Node node) {
				try {
					if (node.getArtifact() != null) {
						DirectoryArtifactData data = (DirectoryArtifactData) node.getArtifact().getData();
						if (data.getPath().equals(path)) {
							result.add((MemNode) node);
						}
					}
				} catch (ClassCastException e) {
					// ignor
				}
			}
		});
		return (result.isEmpty()) ? null : result.get(0);
	}
}
