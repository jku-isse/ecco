package at.jku.isse.ecco.exporter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.pog.PartialOrderGraph.Node;
import at.jku.isse.ecco.pog.PartialOrderGraph.Node.NodeVisitor;
import at.jku.isse.ecco.tree.RootNode;

public class PartialOrderGraphExporter {

	public static void export(PartialOrderGraph graph, Path path) {
		Node head = graph.getHead();
		try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path)) {
			head.traverse(new NodeVisitor() {
				Condition oldCondition = null;

				@Override
				public void visit(Node node) {
					Condition condition = null;
					try {
						if (node.getArtifact() != null) {
							at.jku.isse.ecco.tree.Node memNode = node.getArtifact().getContainingNode();
							while (memNode.getParent() != null) {
								memNode = memNode.getParent();
							}
							if (memNode instanceof RootNode) {
								Association association = ((RootNode) memNode).getContainingAssociation();
								if (association != null)
									condition = association.computeCondition();
							}
							if (condition != null && !condition.equals(oldCondition)) {
								if (oldCondition != null)
									bufferedWriter.write("#endif\n");
								bufferedWriter
										.write("#if " + condition.getSimpleModuleRevisionConditionString() + "\n");
								bufferedWriter.write(node.getArtifact().toString() + "\n");
								oldCondition = condition;
							} else
								bufferedWriter.write(node.getArtifact().toString() + "\n");
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
			bufferedWriter.write("#endif");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
