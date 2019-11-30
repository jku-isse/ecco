package at.jku.isse.ecco.importer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;

import org.apache.commons.io.FileUtils;

import at.jku.isse.ecco.adapter.dispatch.DirectoryArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.text.TextPlugin;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.repository.Repository.Op;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.core.MemAssociation;
import at.jku.isse.ecco.storage.mem.tree.MemNode;
import at.jku.isse.ecco.storage.mem.tree.MemRootNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.Node.NodeVisitor;

import static at.jku.isse.ecco.PreprocessorSyntax.IF;
import static at.jku.isse.ecco.PreprocessorSyntax.ELSE;
import static at.jku.isse.ecco.PreprocessorSyntax.ENDIF;


public class TraceImporterV2 {
	private final Map<Condition, Association.Op> map = new HashMap<>();
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
		System.out.println("Start ...");
		System.out.println("Read directory");
		Map<Path, List<Node.Op>> fileList = createBaseAssociation();
		fileList.forEach((file, nodeList) -> {
			System.out.println("Import " + file);
			importFile(file, nodeList);
		});
		
		map.forEach((key, value) -> repository.addAssociation(value));
		System.out.println(fileList.size() + " files were imported.");
		System.out.println("End");
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
		map.put(new Condition("base"), baseAssociation);
		
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
		Stack<Condition> actualCondition = new Stack<>();
		Stack<Boolean> elseIfCounter = new Stack<>();
		Node.Op[] actualPluginNode = new MemNode[1];
		actualPluginNode[0] = nodeList.get((nodeList.size() - 1));
		Path relPath = fromPath.relativize(file);

		try {
			Files.lines(file)
					.forEach(line -> {
						if (line.matches("^((\\s)*" + IF + " (.)*)")) {
							elseIfCounter.push(false);
							actualCondition.push(new Condition(line.replaceFirst("(\\s)*" + IF + " ", ""), 
									actualAssociations.isEmpty() ? baseAssociation : (Association) actualAssociations.peek()));
							actualPluginNode[0] = startNewBlock(actualCondition.peek(), relPath, repository, nodeList);
						} else if (line.matches("^((\\s)*" + ELSE + ")")) {
							actualPluginNode[0] = endBlock(relPath);
							actualPluginNode[0] = startNewBlock(actualCondition.peek().negate(), relPath, repository, nodeList);
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
							//System.out.println(actualPluginNode[0].getContainingAssociation().computeCondition().getPreprocessorConditionString());
						}
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		exportPOG(pog, repPath, relPath);
	}

	private Node.Op startNewBlock(Condition condition, Path file, Op repository, List<Node.Op> nodeList) {
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
			condition.set(association, repository);
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
