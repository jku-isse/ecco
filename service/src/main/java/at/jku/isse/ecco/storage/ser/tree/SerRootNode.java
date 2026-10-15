package at.jku.isse.ecco.storage.ser.tree;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
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

	/**
	 * How the tree below is written: 1, the nodes breadth first, each followed by where its children
	 * are - null for the nodes that follow, or the {@link PieceRef} of the piece they are (see
	 * {@link TreePieces}).
	 */
	private int treeFormat;

	private void writeObject(ObjectOutputStream out) throws IOException {
		// (the root's own numberOfChildren is a SerNode field, refreshed by SerNode.writeObject(), which
		// runs before this - see there)
		this.treeFormat = 1;
		out.defaultWriteObject();
		// a file's subtree as a piece of its own - when the stream stores pieces (association files);
		// otherwise (remote sync, copies) inline
		PieceSink pieces = out instanceof PieceSink sink ? sink : null;
		// breadth first
		Queue<Node.Op> currentLevel = new LinkedList<>(this.getChildren());
		Queue<Node.Op> nextLevel = new LinkedList<>();
		while (currentLevel.peek() != null){
			for(Node.Op node : currentLevel){
				node.updateNumberOfChildren();
				out.writeObject(node);
				if (pieces != null && TreePieces.isPieceRoot(node)) {
					out.writeObject(new PieceRef(pieces.piece(node)));
				} else {
					out.writeObject(null);
					nextLevel.addAll(node.getChildren());
				}
			}
			currentLevel = nextLevel;
			nextLevel = new LinkedList<>();
		}
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		if (this.treeFormat != 1)
			throw new StreamCorruptedException("An association tree in format " + this.treeFormat + " - this version of ECCO reads format 1.");
		Queue<Node.Op> currentLevel = new LinkedList<>();
		currentLevel.add(this);
		Queue<Node.Op> nextLevel = new LinkedList<>();

		while(currentLevel.peek() != null){
			for (Node.Op node : currentLevel){
				node.setChildren(new ArrayList<>());
				for(int i = 0; i < node.getNumberOfChildren(); i++){
					Node.Op child = (Node.Op) in.readObject();
					node.addChildWithoutNumberUpdate(child);
					Object where = in.readObject();
					if (where instanceof PieceRef piece) {
						if (!(in instanceof PieceSource source))
							throw new StreamCorruptedException("A tree refers to a piece (" + piece.digest() + "), but is not read from a repository's storage.");
						child.setChildren(new ArrayList<>());
						for (Node.Op pieceChild : source.piece(piece.digest()))
							child.addChildWithoutNumberUpdate(pieceChild);
					} else {
						nextLevel.add(child);
					}
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

	/**
	 * A root is always unique ({@link #isUnique()}): Trees.slice() setting it non-unique changed
	 * only the stored field - the same association then serialized differently without having
	 * changed, and was not written (see SerTransactionStrategy#verifyUnchangedAssociations).
	 */
	@Override
	public void setUnique(boolean unique) {
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
