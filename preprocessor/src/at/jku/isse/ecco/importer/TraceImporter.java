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

import org.omg.PortableServer.ImplicitActivationPolicyOperations;

import com.bpodgursky.jbool_expressions.Expression;
import com.bpodgursky.jbool_expressions.parsers.ExprParser;
import com.bpodgursky.jbool_expressions.rules.RuleSet;

import at.jku.isse.ecco.adapter.dispatch.DirectoryArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.text.LineArtifactData;
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
import at.jku.isse.ecco.storage.mem.pog.MemPartialOrderGraph;
import at.jku.isse.ecco.storage.mem.tree.MemNode;
import at.jku.isse.ecco.storage.mem.tree.MemRootNode;
import at.jku.isse.ecco.tree.Node;

public class TraceImporter {
	private static Map<String, MemAssociation> map = new HashMap<>();
	private static Stack<Association> actualAssociations = new Stack<>();
	private static MemNode actualPluginNode;
	private static List<MemArtifact<PluginArtifactData>> pluginsPerFile;

	public static void importFolder(Repository.Op repository, Path fromPath, String fileExtension) {
		try {
			Files.walk(fromPath)
					.filter(file -> file.toString().endsWith(fileExtension))
					.forEach(file -> {
						importFile(repository, file);
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		map.forEach((key, value) -> repository.addAssociation(value));	System.out.println("start");
		map.forEach((key, value) -> System.out.println(value.computeCondition().getPreprocessorConditionString()));
		System.out.println("end");
	}

	public static void importFile(Op repository, Path file) {
		pluginsPerFile = new ArrayList<>();
		List<MemArtifact<LineArtifactData>> lines = new ArrayList<>();
		startNewBlock("(base)", file, repository);

		try {
			Files.lines(file)
					.forEach(line -> {
						if (line.matches("^((\\s)*(#if (.)*|#endif))")) {
							if (line.matches("^((\\s)*#if (.)*)")) {
								String dnfCondition = parseCondition(line);
								//System.out.println(dnfCondition);
								startNewBlock(dnfCondition, file, repository);
							} else {
								endBlock(file);
							}
						} else {
							MemArtifact<LineArtifactData> lineArtifact = fillBlock(line, file);
							lines.add(lineArtifact);
						}
					});
		} catch (IOException e) {
			e.printStackTrace();
		}
		endBlock(file);

		PartialOrderGraph.Op sequenceGraph = new MemPartialOrderGraph();
		sequenceGraph.merge(lines);
		for (MemArtifact<PluginArtifactData> artifact : pluginsPerFile) {
			artifact.setSequenceGraph(sequenceGraph);
		}
	}

	private static String parseCondition(String line) { //TODO add nested condition
		String condition = line.replaceFirst("(\\s)*#if ", "");
		if (!actualAssociations.isEmpty())
			condition = "(" + condition + ")&&" + actualAssociations.peek().computeCondition().getPreprocessorConditionString();
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

	@SuppressWarnings("unchecked")
	private static void startNewBlock(String condition, Path file, Op repository) {
		map.putIfAbsent(condition, new MemAssociation());
		MemAssociation association = map.get(condition);
		Node.Op dirNode;
		if (association.getRootNode() == null) {
			MemRootNode rootNode = new MemRootNode();
			association.setRootNode(rootNode);
			dirNode = new MemNode(new MemArtifact<DirectoryArtifactData>(new DirectoryArtifactData(Paths.get(""))));
			rootNode.setContainingAssociation(association);
			rootNode.addChild(dirNode);
			//set condition
			setCondition(condition, repository, association);
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

	private static MemArtifact<LineArtifactData> fillBlock(String line, Path file) {
		MemArtifact<LineArtifactData> lineArtifact = new MemArtifact<LineArtifactData>(
				new LineArtifactData(line));
		lineArtifact.setAtomic(true); // TODO set ordered
		actualPluginNode.addChild(new MemNode(lineArtifact)); // TODO set unique
		return lineArtifact;
	}

	private static void endBlock(Path file) {
		actualAssociations.pop();
		if(actualAssociations.isEmpty()) actualPluginNode = null;
		else actualPluginNode = getPluginNode(file, actualAssociations.peek());
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
