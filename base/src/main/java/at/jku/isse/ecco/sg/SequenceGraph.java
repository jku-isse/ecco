package at.jku.isse.ecco.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.Persistable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Public sequence graph interface.
 */
public interface SequenceGraph extends Persistable {

	public Node getRoot();


	/**
	 * Private sequence graph interface.
	 */
	public interface Op extends SequenceGraph {

		public Node.Op getRoot();

		public void sequence(at.jku.isse.ecco.tree.Node.Op node) throws EccoException;

		public void sequenceNodes(List<? extends at.jku.isse.ecco.tree.Node.Op> nodes) throws EccoException;

		public void sequenceArtifacts(List<? extends Artifact.Op<?>> artifacts) throws EccoException;

		public int[] align(List<? extends Artifact.Op<?>> artifacts) throws EccoException;


		public void sequence(SequenceGraph.Op other);

		public void updateArtifactReferences();

		public void copy(SequenceGraph.Op other);

//		public Collection<? extends Artifact<?>> getSymbols();


		public void trim(Collection<? extends Artifact.Op<?>> symbols);


		//public Map<Set<Artifact<?>>, SequenceGraphNode> getNodes(); // TODO: this may be unneeded!


		public Collection<? extends Artifact.Op<?>> getSymbols();


		public int getCurrentSequenceNumber();

		public void setCurrentSequenceNumber(int sn);

		public int nextSequenceNumber() throws EccoException;


		public boolean getPol();

		public void setPol(boolean pol);


		public Node.Op createSequenceGraphNode(boolean pol);
	}


	/**
	 * Sequence graph node.
	 */
	public interface Node {
		public Map<? extends Artifact.Op<?>, ? extends Node> getChildren();

		public interface Op extends Node {
			public Map<Artifact.Op<?>, Op> getChildren();

			public boolean getPol();

			public void setPol(boolean pol);
		}
	}

}
