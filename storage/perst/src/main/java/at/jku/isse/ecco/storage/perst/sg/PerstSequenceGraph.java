package at.jku.isse.ecco.storage.perst.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.sg.SequenceGraphOperator;
import org.garret.perst.Persistent;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class PerstSequenceGraph extends Persistent implements SequenceGraph, SequenceGraph.Op {

	private transient SequenceGraphOperator operator = new SequenceGraphOperator(this);


	private Node.Op root = null;

	private int cur_seq_number = 1;

//	private Map<Set<Artifact<?>>, SequenceGraphNode> nodes = new HashMap<>();

//	private List<Artifact<?>> symbols = new ArrayList<>();

	private boolean pol = true;


	public PerstSequenceGraph() {
		this.pol = true;
		this.root = (PerstSequenceGraphNode) this.createSequenceGraphNode(this.pol);
//		this.nodes.put(new HashSet<Artifact<?>>(), this.root);
	}


	@Override
	public Node.Op getRoot() {
		return this.root;
	}

	@Override
	public void sequence(at.jku.isse.ecco.tree.Node.Op node) throws EccoException {
		this.operator.sequence(node);
	}

	@Override
	public void sequenceNodes(List<? extends at.jku.isse.ecco.tree.Node.Op> nodes) throws EccoException {
		this.operator.sequenceNodes(nodes);
	}

	@Override
	public void sequenceArtifacts(List<? extends Artifact.Op<?>> artifacts) throws EccoException {
		this.operator.sequenceArtifacts(artifacts);
	}

	@Override
	public int[] align(List<? extends Artifact.Op<?>> artifacts) throws EccoException {
		return this.operator.align(artifacts);
	}

	@Override
	public void sequence(SequenceGraph.Op other) {
		this.operator.sequence(other);
	}

	@Override
	public void updateArtifactReferences() {
		this.operator.updateArtifactReferences();
	}

	@Override
	public void copy(SequenceGraph.Op other) {
		this.operator.copy(other);
	}

	@Override
	public Collection<? extends Artifact.Op<?>> getSymbols() {
		return this.operator.collectSymbols();
	}

	@Override
	public void trim(Collection<? extends Artifact.Op<?>> symbols) {
		this.operator.trim(symbols);
	}


	// perst

	public void storeRecursively() {
		this.store();

//		Set<SequenceGraphNode> nodes = new HashSet<>();
//		this.collectNodes(this.getRoot(), nodes);

//		// store all nodes
//		for (SequenceGraphNode node : this.nodes.values()) {
//			//if (node instanceof PerstSequenceGraphNode)
//			((PerstSequenceGraphNode) node).store();
//		}

		for (Node node : this.operator.collectNodes()) {
			((PerstSequenceGraphNode) node).store();
		}
	}

	// TODO: this is a bad implementation! every node is visited multiple times!
	protected void collectNodes(Node node, Set<Node> nodeSet) {
		//if (node instanceof PerstSequenceGraphNode) {
		nodeSet.add(node);
		for (Node child : ((PerstSequenceGraphNode) node).getChildren().values()) {
			this.collectNodes(child, nodeSet);
		}
		//}
	}


	// operand

//	public Map<Set<Artifact<?>>, SequenceGraphNode> getNodes() {
//		return this.nodes;
//	}


	public int nextSequenceNumber() throws EccoException {
		if (this.cur_seq_number + 1 < -1)
			throw new EccoException("WARNING: sequence number overflow!");
		return this.cur_seq_number++;
	}


	public int getCurrentSequenceNumber() {
		return this.cur_seq_number;
	}

	@Override
	public void setCurrentSequenceNumber(int sn) {
		this.cur_seq_number = sn;
	}


	public boolean getPol() {
		return this.pol;
	}

	public void setPol(boolean pol) {
		this.pol = pol;
	}


	public Node.Op createSequenceGraphNode(boolean pol) {
		return new PerstSequenceGraphNode(pol);
	}

}
