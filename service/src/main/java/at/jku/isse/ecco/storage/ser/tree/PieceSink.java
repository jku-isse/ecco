package at.jku.isse.ecco.storage.ser.tree;

import at.jku.isse.ecco.tree.Node;

import java.io.IOException;

/**
 * Implemented by the stream an association is written to when its files' subtrees are stored as
 * pieces of their own (see {@link TreePieces}): takes a file's subtree and gives the digest it is
 * stored under. A stream without it (remote sync, copies) gets the whole tree inline.
 */
public interface PieceSink {

	/** Stores (or finds stored) the subtree below [pieceRoot]; its digest. */
	String piece(Node.Op pieceRoot) throws IOException;
}
