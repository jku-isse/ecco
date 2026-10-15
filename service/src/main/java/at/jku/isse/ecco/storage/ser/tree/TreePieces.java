package at.jku.isse.ecco.storage.ser.tree;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Queue;

/**
 * An association's tree is stored cut at its files: the subtree below each file's node (its
 * plugin node) is a piece of its own, stored under the digest of its bytes, and the association
 * refers to it (gap #48). A commit changing a few files of a large association writes the pieces
 * of those files only; a piece already stored - the same file content in another association, or
 * in the same one under a new id after a commit split it - is not written again.
 * <p>
 * A piece is the file node's children, breadth first, in a stream of its own (as an association's
 * tree, see {@link SerRootNode}), so the same subtree always has the same bytes. Read, it gives new
 * nodes each time: a node belongs to one tree (Trees relies on its parent).
 */
public final class TreePieces {

	private static final int VERSION = 1;

	private TreePieces() {
	}

	/** Whether the subtree below [node] is a piece: a file's node with content. */
	public static boolean isPieceRoot(Node.Op node) {
		return node.getArtifact() != null && node.getArtifact().getData() instanceof PluginArtifactData && !node.getChildren().isEmpty();
	}

	/** The bytes of the piece below [pieceRoot]. */
	public static byte[] write(Node.Op pieceRoot) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeInt(VERSION);
			out.writeInt(pieceRoot.getChildren().size());
			Queue<Node.Op> level = new ArrayDeque<>(pieceRoot.getChildren());
			while (!level.isEmpty()) {
				Queue<Node.Op> next = new ArrayDeque<>();
				for (Node.Op node : level) {
					node.updateNumberOfChildren();
					out.writeObject(node);
					next.addAll(node.getChildren());
				}
				level = next;
			}
		}
		return bytes.toByteArray();
	}

	/** The nodes a piece's [bytes] hold: new ones, the file node's children, with their subtrees. */
	public static List<Node.Op> read(byte[] bytes) throws IOException, ClassNotFoundException {
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
			int version = in.readInt();
			if (version != VERSION)
				throw new StreamCorruptedException("A tree piece of version " + version + " - this version of ECCO reads version " + VERSION + ".");
			int count = in.readInt();
			List<Node.Op> children = new ArrayList<>(count);
			Queue<Node.Op> level = new ArrayDeque<>();
			for (int i = 0; i < count; i++) {
				Node.Op child = (Node.Op) in.readObject();
				children.add(child);
				level.add(child);
			}
			while (!level.isEmpty()) {
				Queue<Node.Op> next = new ArrayDeque<>();
				for (Node.Op node : level) {
					int children2 = node.getNumberOfChildren();
					node.setChildren(new ArrayList<>());
					for (int i = 0; i < children2; i++) {
						Node.Op child = (Node.Op) in.readObject();
						node.addChildWithoutNumberUpdate(child);
						next.add(child);
					}
				}
				level = next;
			}
			return children;
		}
	}

	/** The digest a piece of [bytes] is stored under: its SHA-256, hexadecimal. */
	public static String digest(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available.", e);
		}
	}

	/** A sink that only computes digests - for comparing an association's serialization, not storing it. */
	public static final PieceSink DIGEST_ONLY = pieceRoot -> digest(write(pieceRoot));
}
