package at.jku.isse.ecco.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;

import at.jku.isse.ecco.adapter.dispatch.DirectoryArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.text.LineArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.repository.Repository.Op;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.core.MemAssociation;
import at.jku.isse.ecco.storage.mem.pog.MemPartialOrderGraph;
import at.jku.isse.ecco.storage.mem.tree.MemNode;
import at.jku.isse.ecco.storage.mem.tree.MemRootNode;
import at.jku.isse.ecco.tree.Node;

public class TraceImporter {
	private static Map<String, MemAssociation> map = new HashMap<>();
	private static Stack<Association> actualAssociations = new Stack<>();
	private static MemNode actualPluginNode;
	private static List<MemArtifact<PluginArtifactData>> pluginsPerFile;

	public static void importTrace(Repository.Op repository, Path fromPath) {
		try {
			Files.walk(fromPath)
					.filter(file -> file.toString().endsWith(".txt"))
					.forEach(file -> {
						pluginsPerFile = new ArrayList<>();
						List<MemArtifact<LineArtifactData>> lines = new ArrayList<>();
						startNewBlock("base", file);

						try {
							Files.lines(file)
									.forEach(line -> {
										if (line.matches("^((\\s)*(#if (.)*|#endif))")) {
											if (line.matches("^((\\s)*#if (.)*)")) {
												String condition = line.replaceFirst("(\\s)*#if ", "");
												addNewFeatures(condition, repository);
												startNewBlock(condition, file);
											} else {
												endBlock(file);
											}
										} else {
											MemArtifact<LineArtifactData> lineArtifact = new MemArtifact<LineArtifactData>(
													new LineArtifactData(line));
											lineArtifact.setAtomic(true); // TODO set ordered
											fillBlock(lineArtifact, file);
											lines.add(lineArtifact);
										}
									});
						} catch (IOException e) {
							e.printStackTrace();
						}

						PartialOrderGraph.Op sequenceGraph = new MemPartialOrderGraph();
						sequenceGraph.merge(lines);
						for (MemArtifact<PluginArtifactData> artifact : pluginsPerFile) {
							artifact.setSequenceGraph(sequenceGraph);
						}
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("end");
	}

	private static void addNewFeatures(String condition, Op repository) {
		String[] features = condition.split("\\s*(&&|\\|\\||\\(|\\))\\s*");
		Arrays.stream(features).filter(f -> f.length() > 0).forEach(f -> repository.addFeature("" + f.hashCode(), f));
		// ev return von Liste
	}

	@SuppressWarnings("unchecked")
	private static void startNewBlock(String condition, Path file) {
		map.putIfAbsent(condition, new MemAssociation());
		MemAssociation association = map.get(condition);
		Node.Op dirNode;
		if (association.getRootNode() == null) {
			MemRootNode rootNode = new MemRootNode();
			association.setRootNode(rootNode);
			dirNode = new MemNode(new MemArtifact<DirectoryArtifactData>(new DirectoryArtifactData(Paths.get(""))));
			rootNode.setContainingAssociation(association);
			rootNode.addChild(dirNode);
		} else
			dirNode = association.getRootNode().getChildren().get(0);

		// while() { //TODO set for every folder own Node
		// Path relativePath = fromPath.relativize(file);
		// MemNode child = new MemNode(new
		// MemArtifact<DirectoryArtifactData>(new
		// DirectoryArtifactData(relativePath)));
		// dirNode.addChild(child);
		// dirNode = child;
		// }
		MemNode pluginNode = getPluginNode(file, association);
		if (pluginNode == null) {
			pluginNode = new MemNode(
					new MemArtifact<PluginArtifactData>(new PluginArtifactData(UUID.randomUUID().toString(), file)));
			dirNode.addChild(pluginNode);
		}

		actualPluginNode = pluginNode;
		pluginsPerFile.add((MemArtifact<PluginArtifactData>) pluginNode.getArtifact()); // safe cast
		actualAssociations.push(association);
	}

	private static void fillBlock(MemArtifact<LineArtifactData> lineArtifact, Path file) {
		actualPluginNode.addChild(new MemNode(lineArtifact)); // TODO set unique
	}

	private static void endBlock(Path file) {
		actualAssociations.pop();
		actualPluginNode = getPluginNode(file, actualAssociations.peek());
	}

	private static MemNode getPluginNode(Path file, Association association) { // TODO muss nicht in Ebene 2 sein
		for (Node node : association.getRootNode().getChildren().get(0).getChildren()) {
			PluginArtifactData data = (PluginArtifactData) node.getArtifact().getData();
			if (data.getPath().equals(file)) {
				return (MemNode) node;
			}
		}
		return null;
	}
}
