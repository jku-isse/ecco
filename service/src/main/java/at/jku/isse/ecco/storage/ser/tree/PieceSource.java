package at.jku.isse.ecco.storage.ser.tree;

import at.jku.isse.ecco.tree.Node;

import java.io.IOException;
import java.util.List;

/** Implemented by the stream an association is read from when its tree refers to pieces (see {@link TreePieces}). */
public interface PieceSource {

	/** The piece [digest] - the children of a file's node, read into new nodes. */
	List<Node.Op> piece(String digest) throws IOException, ClassNotFoundException;
}
