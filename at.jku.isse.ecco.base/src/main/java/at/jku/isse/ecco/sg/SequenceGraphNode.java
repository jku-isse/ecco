package at.jku.isse.ecco.sg;

import at.jku.isse.ecco.artifact.Artifact;

import java.util.Map;

public interface SequenceGraphNode {

	public boolean getPol();

	public void setPol(boolean pol);

	public Map<Artifact<?>, SequenceGraphNode> getChildren();

}
