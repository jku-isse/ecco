package at.jku.isse.ecco.sequenceGraph;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.tree.Node;

import java.util.List;

public interface SequenceGraph {

	public SequenceGraphNode getRoot();


	public void sequence(Node node) throws EccoException;

	public void sequenceNodes(List<Node> nodes) throws EccoException;

	public void sequenceArtifacts(List<Artifact<?>> artifacts) throws EccoException;

	public int[] align(List<Artifact<?>> artifacts) throws EccoException;

}
