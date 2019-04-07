package at.jku.isse.ecco.exporter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.pog.PartialOrderGraph.Node;
import at.jku.isse.ecco.pog.PartialOrderGraph.Node.NodeVisitor;

public class PartialOrderGraphExporter {
	
	public static void export(PartialOrderGraph graph, Path path) {
		Node head = graph.getHead();
		try	(BufferedWriter bufferedWriter = Files.newBufferedWriter(path)) {
			head.traverse(new NodeVisitor() {				
				@Override
				public void visit(Node node) {
					try {
						if(node.getArtifact()!=null)
						bufferedWriter.write(node.getArtifact().toString());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
}
