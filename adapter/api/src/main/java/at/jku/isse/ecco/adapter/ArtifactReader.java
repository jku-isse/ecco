package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;

import java.util.List;
import java.util.Map;

/**
 * An ArtifactReader is responsible for transforming a particular type of artifact into an Ecco tree.
 *
 * @param <I> The input to the reader.
 * @param <O> The output of the reader.
 */
public interface ArtifactReader<I, O> {

	public String getPluginId();


	public Map<Integer, String[]> getPrioritizedPatterns();


	public O read(I base, I[] input);

	public O read(I[] input);


	public void addListener(ReadListener listener);

	public void removeListener(ReadListener listener);

	/**
	 * Everything besides a file's own content that this reader's tree of it depends on - settings, a
	 * file beside it - as a string that changes whenever that does; or null if its trees are not to be
	 * reused. A reader that answers lets the dispatcher reuse the tree of a file it read before with
	 * the same content and the same context, instead of reading it again (in a Git import, every
	 * commit reads every file, most of them unchanged). Its trees must then depend on nothing else,
	 * and their artifacts must not reference each other.
	 *
	 * @param base The base directory the files are read from
	 * @return The context of this reader's trees, or null
	 */
	public default String readContext(I base) {
		return null;
	}

	/**
	 * The positions - classes, methods, functions, sections - of a file this reader read, with their
	 * lines: {@code fileNode} is the file's node as {@link #read} returned it. Empty if the reader
	 * knows none, which leaves changes to its files located by file only.
	 *
	 * @param fileNode The node of a file this reader read
	 * @return The file's positions, nested ones included, in any order
	 */
	public default List<SourcePosition> positions(Node fileNode) {
		return List.of();
	}

}
