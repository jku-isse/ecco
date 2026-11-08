package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;

import java.util.Collection;

/**
 * A part of a file a change can be in - a class, a method, a function, a section - with its
 * lines, 1-based and inclusive. A reader names the positions of the files it reads (see
 * {@link ArtifactReader#positions}); the Git import's feature suggestion locates a commit's changes
 * by them, to tell which known features changed the same places before.
 *
 * @param kind what kind of position, e.g. "class", "method", "function", "section"
 * @param name the position's name within its file, e.g. "ZipFile.addFiles" or "Installation"
 */
public record SourcePosition(String kind, String name, int startLine, int endLine) {

	public boolean contains(int line) {
		return this.startLine <= line && line <= this.endLine;
	}

	/**
	 * The first and last line of the {@code Location} properties (see {@link Location}) on
	 * {@code node} and the nodes below it - how the C and C++ readers record lines - or null if none.
	 */
	public static int[] locatedLines(Node node) {
		int[] lines = null;
		java.util.Optional<Location> location = node.getProperty("Location");
		if (location.isPresent())
			lines = new int[]{location.get().getStartLine(), location.get().getEndLine()};
		for (Node child : node.getChildren()) {
			int[] inner = locatedLines(child);
			if (inner != null)
				lines = lines == null ? inner : new int[]{Math.min(lines[0], inner[0]), Math.max(lines[1], inner[1])};
		}
		return lines;
	}

	/** The innermost (smallest) of {@code positions} that contains {@code line}, or null. */
	public static SourcePosition innermost(Collection<SourcePosition> positions, int line) {
		SourcePosition innermost = null;
		for (SourcePosition position : positions)
			if (position.contains(line) && (innermost == null || position.endLine - position.startLine < innermost.endLine - innermost.startLine))
				innermost = position;
		return innermost;
	}
}
