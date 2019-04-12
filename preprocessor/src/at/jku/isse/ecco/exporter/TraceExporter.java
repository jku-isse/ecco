package at.jku.isse.ecco.exporter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.Node.NodeVisitor;

/**
 * 
 * @author Simon Eilmsteiner
 *
 */
public class TraceExporter {

	public static void exportAssociations(Collection<Association> associations, Path toPath) {
		if (associations == null || toPath == null)
			throw new IllegalArgumentException("The argument(s) cannot be null");
		Set<Path> processedFiles = new HashSet<>();
		for (Association association : associations) {
			exportAssociation(association, toPath, processedFiles);
		}
	}

	/**
	 * 
	 * @param association
	 * @param toPath
	 * @param processedFiles
	 *            Use an empty list if there are no already processed files or
	 *            if you want to reprocess them.
	 */
	public static void exportAssociation(Association association, Path toPath, Set<Path> processedFiles) {
		if (association == null || toPath == null || association.getRootNode() == null
				|| association.computeCondition() == null || processedFiles == null)
			throw new IllegalArgumentException("The argument(s) cannot be null");
		association.getRootNode().traverse(new NodeVisitor() {

			@Override
			public void visit(Node node) {
				try {
					Artifact<?> artifact = node.getArtifact();
					if (artifact != null) {
						PluginArtifactData pad = (PluginArtifactData) artifact.getData();
						if (pad.getFileName().toString().endsWith(".txt")) {
							if (processedFiles.add(pad.getPath())) {
								if (artifact.isSequenced())
									PartialOrderGraphExporter.export(artifact.getSequenceGraph(), toPath.resolve(pad.getFileName()));
								else
									; // TODO ist das möglich? wenn ja wie wird
										// das
										// behandelt
							}
						}
					}
				} catch (ClassCastException e) {
					System.out.println("wrong type");
					// ignor all other artifacts
				}

			}
		});
	}
}
