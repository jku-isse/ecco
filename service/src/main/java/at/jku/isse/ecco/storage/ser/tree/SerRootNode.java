package at.jku.isse.ecco.storage.ser.tree;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class SerRootNode extends SerNode implements RootNode, RootNode.Op {

	public static final long serialVersionUID = 1L;


	private Association.Op containingAssociation;


	public SerRootNode() {
		//super(new MemArtifact<>(new StringArtifactData("ROOT")));
		super();
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
		// breadth first
		Queue<Node.Op> currentLevel = new LinkedList<>(this.getChildren());
		Queue<Node.Op> nextLevel = new LinkedList<>();
		while (currentLevel.peek() != null){
			for(Node.Op node : currentLevel){
				node.updateNumberOfChildren();
				out.writeObject(node);
				nextLevel.addAll(node.getChildren());
			}
			currentLevel = nextLevel;
			nextLevel = new LinkedList<>();
		}
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		Queue<Node.Op> currentLevel = new LinkedList<>();
		currentLevel.add(this);
		Queue<Node.Op> nextLevel = new LinkedList<>();

		while(currentLevel.peek() != null){
			for (Node.Op node : currentLevel){
				node.setChildren(new ArrayList<>());
				for(int i = 0; i < node.getNumberOfChildren(); i++){
					Node.Op child = (Node.Op) in.readObject();
					node.addChildWithoutNumberUpdate(child);
					nextLevel.add(child);
				}
			}
			currentLevel = nextLevel;
			nextLevel = new LinkedList<>();
		}
	}


	@Override
	public boolean isUnique() {
		return true;
	}

	@Override
	public boolean isAtomic() {
		return false;
	}


	@Override
	public RootNode.Op createNode(Artifact.Op<?> artifact) {
		return new SerRootNode();
	}


	@Override
	public void setContainingAssociation(Association.Op containingAssociation) {
		this.containingAssociation = containingAssociation;
	}

	@Override
	public Association.Op getContainingAssociation() {
		return this.containingAssociation;
	}

	@Override
	public Node.Op copySingleNode(boolean copyFeatureTrace){
		SerRootNode newNode = new SerRootNode();
		return newNode;
	}

}
