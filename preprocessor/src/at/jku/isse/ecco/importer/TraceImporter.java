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
import java.util.List;
import java.util.Map;
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


public class TraceImporter {
	private static Map<String, MemAssociation> map = new HashMap<>();
	private static Stack<Association> actualAssociations = new Stack<>();
	private static MemNode actualPluginNode;

	public static void importFolder(Repository.Op repository, Path fromPath, Path repPath, String fileExtension,
			LineImporter lineImporter) {
		try {
			Files.walk(fromPath)
					.filter(file -> file.toString().endsWith(fileExtension))
					.forEach(file -> {
						importFile(repository, file, fromPath, repPath, lineImporter);
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		map.forEach((key, value) -> repository.addAssociation(value));
		map.forEach((key, value) -> System.out.println(value.computeCondition().getPreprocessorConditionString())); //TODO remove
		System.out.println("end");
	}

	public static void importFile(Repository.Op repository, Path file, Path fromPath, Path repPath,
			LineImporter lineImporter) {
		Path relPath = fromPath.relativize(file);
		PartialOrderGraph.Op pog = startNewBlock("(base)", relPath, repository);
		Stack<String> actualCondition = new Stack<>();

		try {		
			Files.lines(file)
					.forEach(line -> {
						if (line.matches("^((\\s)*" + IF + " (.)*)")) {
							actualCondition.push(line.replaceFirst("(\\s)*" + IF + " ", ""));
							String dnfCondition = parseCondition(actualCondition.peek());
							startNewBlock(dnfCondition, relPath, repository);
						} else if (line.matches("^((\\s)*" + ELSE + ")")) {
							endBlock(relPath);
							String dnfCondition = parseCondition("!(" + actualCondition.peek() + ")");
							startNewBlock(dnfCondition, relPath, repository);
						} else if (line.matches("^((\\s)*" + ENDIF + ")")) {
							actualCondition.pop();
							endBlock(relPath);
						} else {
							lineImporter.importLine(line, actualPluginNode, pog);
						}
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		endBlock(relPath);
		exportPOG(pog, repPath, relPath);
	}

	private static String parseCondition(String condition) {
		if (!actualAssociations.isEmpty())
			condition = "(" + condition + ")&&"
					+ actualAssociations.peek().computeCondition().getPreprocessorConditionString();
		condition = condition.replace("||", "|");
		condition = condition.replace("&&", "&");
		Expression<String> expr = ExprParser.parse(condition);
		expr = RuleSet.toDNF(expr);
		return expr.toString();
	}

	private static List<List<Feature>> addNewFeatures(String condition, Op repository) {
		List<List<Feature>> featureLists = new ArrayList<>(2);
		featureLists.add(new ArrayList<>());
		featureLists.add(new ArrayList<>());
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

	private static PartialOrderGraph.Op startNewBlock(String condition, Path file, Op repository) {
		Association baseAssociation = null;
		if (!actualAssociations.isEmpty())
			baseAssociation = actualAssociations.firstElement();

		map.putIfAbsent(condition, new MemAssociation());
		MemAssociation association = map.get(condition);
		association.setId(UUID.randomUUID().toString());
		Node.Op dirNode;
		if (association.getRootNode() == null) {
			MemRootNode rootNode = new MemRootNode();
			association.setRootNode(rootNode);

			if (baseAssociation == null) {
				dirNode = new MemNode(new MemArtifact<>(new DirectoryArtifactData(Paths.get(""))));
				dirNode.setUnique(true);
				dirNode.getArtifact().setContainingNode(dirNode);
			} else {
				dirNode = new MemNode(
						(Artifact.Op<?>) baseAssociation.getRootNode().getChildren().get(0).getArtifact());
				dirNode.setUnique(false);
			}
			rootNode.setContainingAssociation(association);
			rootNode.addChild(dirNode);
			//set condition
			setCondition(condition, repository, association);
		} else
			dirNode = association.getRootNode().getChildren().get(0);
		//set folder artifact
		if (file.getParent() != null) {
			Path resolvedPath = Paths.get("");
			for (Path path : file.getParent()) {
				resolvedPath = resolvedPath.resolve(path);
				MemNode newDirNode = getDirNode(resolvedPath, association);
				if (newDirNode == null) {
					if (baseAssociation == null) {
						MemArtifact<DirectoryArtifactData> dirArtifact = new MemArtifact<>(
								new DirectoryArtifactData(resolvedPath));
						newDirNode = new MemNode(dirArtifact);
						newDirNode.setUnique(true);
						dirArtifact.setContainingNode(newDirNode);
					} else {
						newDirNode = new MemNode(getDirNode(resolvedPath, baseAssociation).getArtifact());
						newDirNode.setUnique(false);
					}
					dirNode.addChild(newDirNode);
				}
				dirNode = newDirNode;
			}
		}
		//set plug-in artifact
		MemNode pluginNode = getPluginNode(file, association);
		if (pluginNode == null) {
			if (baseAssociation == null) {
				MemArtifact<PluginArtifactData> pluginArtifact = new MemArtifact<>(
						new PluginArtifactData(TextPlugin.class.getName(), file), true);
				pluginNode = new MemNode(pluginArtifact);
				pluginNode.setUnique(true);
				pluginArtifact.setContainingNode(pluginNode);
				pluginArtifact.setSequenceGraph(pluginArtifact.createSequenceGraph());
			} else {
				pluginNode = new MemNode(getPluginNode(file, baseAssociation).getArtifact());
				pluginNode.setUnique(false);
			}
			dirNode.addChild(pluginNode);
		}

		actualPluginNode = pluginNode;
		actualAssociations.push(association);
		return pluginNode.getArtifact().getSequenceGraph();
	}

	private static void setCondition(String condition, Repository.Op repository, MemAssociation association) {
		condition = condition.substring(1, condition.length() - 1);
		String[] conjunctions = condition.split("\\|");
		for (String conjunction : conjunctions) {
			List<List<Feature>> featureLists = addNewFeatures(conjunction, repository);
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

	private static void endBlock(Path file) {
		actualAssociations.pop();
		if (actualAssociations.isEmpty())
			actualPluginNode = null;
		else
			actualPluginNode = getPluginNode(file, actualAssociations.peek());
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
	private static MemNode getPluginNode(Path file, Association association) {
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
	private static MemNode getDirNode(Path path, Association association) {
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
