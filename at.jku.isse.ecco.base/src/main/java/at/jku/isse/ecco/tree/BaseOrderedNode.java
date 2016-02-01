package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.sequenceGraph.BaseSequenceGraph;
import at.jku.isse.ecco.sequenceGraph.SequenceGraph;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class BaseOrderedNode extends BaseNode implements OrderedNode {

	private final List<Node> orderedChildren = new ArrayList<>();

	private boolean sequenced;
	private boolean aligned;
	private SequenceGraph sequenceGraph;


	@Override
	public Node slice(Node other) throws EccoException {
		OrderedNode orderedThis = (OrderedNode) this;
		OrderedNode orderedOther = (OrderedNode) other;

		if (orderedThis.isSequenced() && orderedOther.isSequenced()) {
			if (orderedThis.getSequenceGraph() != orderedOther.getSequenceGraph())
				throw new EccoException("Sequence Graphs did not match!");
		} else if (!orderedThis.isSequenced() && !orderedOther.isSequenced()) {
			orderedThis.setSequenceGraph(orderedThis.createSequenceGraph());
			orderedThis.getSequenceGraph().sequence(orderedThis);
		}

		if (orderedThis.isSequenced() && !orderedOther.isSequenced()) {
			orderedThis.getSequenceGraph().sequence(orderedOther);
		} else if (!orderedThis.isSequenced() && orderedOther.isSequenced()) {
			orderedOther.getSequenceGraph().sequence(orderedThis);
			throw new EccoException("Left node was not sequenced but right node was.");
		}

		orderedThis.getOrderedChildren().clear();
		orderedOther.getOrderedChildren().clear();

		OrderedNode intersection = (OrderedNode) super.slice(orderedOther);
		intersection.setSequenced(true);
		intersection.setSequenceGraph(orderedThis.getSequenceGraph());

		return intersection;
	}

	@Override
	public Node createNode() {
		return new BaseOrderedNode();
	}

	@Override
	public SequenceGraph createSequenceGraph() {
		return new BaseSequenceGraph();
	}


	@Override
	public void addChild(Node child) {
		checkNotNull(child);

		orderedChildren.add(child);
		child.setParent(this);
	}

	@Override
	public SequenceGraph getSequenceGraph() {
		return sequenceGraph;
	}

	@Override
	public boolean isAligned() {
		return aligned;
	}

	@Override
	public boolean isSequenced() {
		return sequenced;
	}

	@Override
	public void removeChild(Node child) {
		checkNotNull(child);

		orderedChildren.remove(child);
	}

	@Override
	public void sequence() {
		if (!this.isSequenced()) {
			sequenceGraph = this.createSequenceGraph();
			sequenceGraph.sequence(this);
		}
	}

	@Override
	public void setAligned(boolean aligned) {
		this.aligned = aligned;
	}

	@Override
	public void setSequenced(boolean sequenced) {
		this.sequenced = sequenced;
	}

	@Override
	public void setSequenceGraph(SequenceGraph sequenceGraph) {
		checkNotNull(sequenceGraph);

		this.sequenceGraph = sequenceGraph;
	}

	@Override
	public List<Node> getOrderedChildren() {
		return orderedChildren;
	}

	@Override
	public void setOrderedChildren(List<Node> children) {
		checkNotNull(children);

		orderedChildren.clear();
		orderedChildren.addAll(children);
	}


	// TODO: the following is probably a bad idea because the behaviour is hard to predict

//	@Override
//	public List<Node> getAllChildren() {
//		if (!this.sequenced)
//			return this.orderedChildren;
//		else
//			return super.getAllChildren();
//	}
//
//	@Override
//	public List<Node> getUniqueChildren() {
//		if (!this.sequenced)
//			return this.orderedChildren;
//		else
//			return super.getUniqueChildren();
//	}

}
