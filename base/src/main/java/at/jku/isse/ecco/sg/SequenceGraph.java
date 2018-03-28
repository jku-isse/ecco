package at.jku.isse.ecco.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.Persistable;

import java.util.Collection;
import java.util.List;

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
	public interface Node extends Persistable {
		//public Map<? extends Artifact.Op<?>, ? extends Node> getChildren();
		public Collection<? extends SequenceGraph.Transition> getChildren();

		public interface Op extends Node {
			@Override
			public Collection<SequenceGraph.Transition.Op> getChildren();

			public boolean getPol();

			public void setPol(boolean pol);

			public Transition.Op addTransition(Artifact.Op<?> key, SequenceGraph.Node.Op value);
		}
	}

	public interface Transition extends Persistable {
		public Artifact<?> getKey();

		public SequenceGraph.Node getValue();

		public interface Op extends Transition {
			@Override
			public Artifact.Op<?> getKey();

			@Override
			public SequenceGraph.Node.Op getValue();
		}
	}

}
