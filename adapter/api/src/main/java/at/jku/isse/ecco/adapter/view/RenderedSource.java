package at.jku.isse.ecco.adapter.view;

import at.jku.isse.ecco.tree.Node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A file's source text as an adapter's writer produces it, with the part of the text each tree node
 * produced ({@link Span}) - what {@link SourceSpanViewer} shows. Spans nest like the tree; a
 * character belongs to the smallest span that contains it.
 */
public final class RenderedSource {

	/** {@code node} produced the text from {@code start} (inclusive) to {@code end} (exclusive). */
	public record Span(Node node, int start, int end) {
	}

	/** A run of one line's characters that belong to the same node ({@code null}: to none). */
	public record Segment(String text, Node node, boolean selected) {
	}

	private final String text;
	private final List<Span> spans;

	public RenderedSource(String text, List<Span> spans) {
		this.text = checkNotNull(text);
		for (Span span : spans)
			checkArgument(0 <= span.start() && span.start() <= span.end() && span.end() <= text.length(), "span outside the text: %s", span);
		this.spans = List.copyOf(spans);
	}

	public String getText() {
		return this.text;
	}

	public List<Span> getSpans() {
		return this.spans;
	}

	/**
	 * The text split into lines of segments, each a run of characters with the same owning node -
	 * the node of the smallest span containing them. Characters of {@code selectedNode}'s span are
	 * marked selected: its own span, or else the span of its nearest ancestor that has one.
	 */
	public List<List<Segment>> lines(Node selectedNode) {
		int length = this.text.length();
		Node[] owner = new Node[length];
		// larger spans first, so smaller (nested) ones overwrite them
		List<Span> bySize = new ArrayList<>(this.spans);
		bySize.sort(Comparator.comparingInt((Span span) -> span.end() - span.start()).reversed());
		for (Span span : bySize)
			for (int i = span.start(); i < span.end(); i++)
				owner[i] = span.node();

		boolean[] selected = new boolean[length];
		Span selectedSpan = this.spanOf(selectedNode);
		if (selectedSpan != null)
			for (int i = selectedSpan.start(); i < selectedSpan.end(); i++)
				selected[i] = true;

		List<List<Segment>> lines = new ArrayList<>();
		List<Segment> line = new ArrayList<>();
		int segmentStart = 0;
		for (int i = 0; i <= length; i++) {
			boolean lineEnd = i == length || this.text.charAt(i) == '\n';
			boolean boundary = lineEnd || i > segmentStart && (owner[i] != owner[segmentStart] || selected[i] != selected[segmentStart]);
			if (boundary && i > segmentStart)
				line.add(new Segment(this.text.substring(segmentStart, i), owner[segmentStart], selected[segmentStart]));
			if (lineEnd) {
				// a final newline ends the last line rather than starting an empty one
				if (i < length || !line.isEmpty() || lines.isEmpty())
					lines.add(line);
				line = new ArrayList<>();
				segmentStart = i + 1;
			} else if (boundary) {
				segmentStart = i;
			}
		}
		return lines;
	}

	/** The line {@code node}'s text starts on (or its nearest ancestor's with a span), or -1. */
	public int lineOf(Node node) {
		Span span = this.spanOf(node);
		if (span == null)
			return -1;
		int line = 0;
		for (int i = 0; i < span.start(); i++)
			if (this.text.charAt(i) == '\n')
				line++;
		return line;
	}

	private Span spanOf(Node node) {
		for (Node current = node; current != null; current = current.getParent())
			for (Span span : this.spans)
				if (span.node() == current)
					return span;
		return null;
	}

	/**
	 * The offset of a position given as a 1-based line and 0-based column in characters - the
	 * form positions come in from libcst.
	 */
	public static int offset(String text, int line, int column) {
		int offset = 0;
		for (int l = 1; l < line; l++) {
			int next = text.indexOf('\n', offset);
			if (next < 0)
				return text.length();
			offset = next + 1;
		}
		return Math.min(offset + column, text.length());
	}
}
