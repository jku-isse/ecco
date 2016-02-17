package at.jku.isse.ecco.sequenceGraph;

import at.jku.isse.ecco.artifact.Artifact;

import java.util.Map;
import java.util.Set;

public interface SequenceGraph {

	public SequenceGraphNode getRoot();

	public Map<Set<Artifact<?>>, SequenceGraphNode> getNodes();


	public int getCurrentSequenceNumber();

	public int nextSequenceNumber();


	public boolean getPol();

	public void setPol(boolean pol);

	public int getCurrentBestCost();

	public void setCurrentBestCost(int cost);


	public SequenceGraphNode createSequenceGraphNode(boolean pol);


//	public int getCurSeqNumber();
//
//	public void sequence(Node node) throws EccoException;
//
//	public void sequenceNodes(List<Node> nodes) throws EccoException;
//
//	public void sequenceArtifacts(List<Artifact<?>> artifacts) throws EccoException;
//
//	public int[] align(List<Artifact<?>> artifacts) throws EccoException;

}
