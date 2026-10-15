package at.jku.isse.ecco.storage.ser.tree;

import java.io.Serial;
import java.io.Serializable;

/** In an association's tree: the children of the node before it are the piece [digest] (see {@link TreePieces}). */
public record PieceRef(String digest) implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;
}
