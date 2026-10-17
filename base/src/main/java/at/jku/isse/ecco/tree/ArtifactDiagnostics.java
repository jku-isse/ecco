package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.pog.PartialOrderGraph;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only rendering for {@code ORDER} checkout diagnostics ({@code Checkout#getOrderWarnings()}):
 * nodes whose ordered artifact had more than one valid relative order for its children during
 * composition, where {@code DefaultOrderSelector} picked one arbitrarily. Purely a traversal of the
 * already-composed tree -- no mutation, no alternative-order enumeration (that needs {@code
 * PartialOrderGraph}'s factorial-blowup-prone linearization methods, deliberately avoided here).
 * {@link #suggestOrderFix} is the one exception, and only barely: it queries {@link
 * PartialOrderGraph#hasUnresolvedOrder}, a single bounded graph walk (no enumeration, no
 * permutations), to tell a genuinely-actionable warning apart from one the graph's accumulated
 * history triggered for content that isn't even part of this composition.
 */
public final class ArtifactDiagnostics {

	private ArtifactDiagnostics() {
	}

	/**
	 * Breadcrumb path from root to the node's artifact, e.g. {@code "parent > artifact"} -- extracted
	 * verbatim from {@code EccoService.checkout()}'s original inline ORDER-line-building loop,
	 * unchanged behavior.
	 */
	public static String describePath(Node node) {
		List<String> pathList = new LinkedList<>();
		Node current = node.getParent();
		while (current != null) {
			if (current.getArtifact() != null)
				pathList.add(0, current.getArtifact().toString() + " > ");
			current = current.getParent();
		}
		pathList.add(String.valueOf(node.getArtifact()));
		return String.join("", pathList);
	}

	// same node-property convention already used by both the text and lilypond adapters to record
	// which source line(s) an artifact came from -- base can't depend on either adapter module (it's
	// the other way around), so the key strings are duplicated here rather than referencing e.g.
	// TextReader.PROPERTY_LINE_START. Empirically confirmed (real commit -> close -> reopen ->
	// checkout) to survive a genuine serialization round-trip for the SER backend, despite Node's
	// own javadoc calling node properties in general "volatile ... not persisted".
	private static final String LINE_START_PROPERTY = "LINE_START";
	private static final String LINE_END_PROPERTY = "LINE_END";

	/**
	 * The ambiguous node's current children in their current (arbitrarily-picked) order, one entry
	 * per child, each annotated with its source line range when the adapter tracked one (e.g. {@code
	 * "X (line 2)"}, or just {@code "X"} if unavailable) -- a read-only reference for a human deciding
	 * whether/how to reorder them.
	 *
	 * <p>Kept defensive against a node with zero children (should not happen for a genuinely
	 * order-ambiguous node, which by definition has >= 2 children, but rather than silently render a
	 * blank/misleading "()" if it ever does, say so.
	 */
	public static List<String> describeChildrenWithLines(Node node) {
		List<String> children = node.getChildren().stream()
				.map(ArtifactDiagnostics::describeChildWithLine)
				.collect(Collectors.toList());
		if (children.isEmpty()) {
			return List.of("(unable to determine current children -- check the checked-out file directly)");
		}
		return children;
	}

	/** The first source line of [node] the adapter recorded, or null if it recorded none. */
	public static Integer lineStart(Node node) {
		Optional<Integer> line = node.getProperty(LINE_START_PROPERTY);
		return line.orElse(null);
	}

	/** The last source line of [node] the adapter recorded, or null if it recorded none. */
	public static Integer lineEnd(Node node) {
		Optional<Integer> line = node.getProperty(LINE_END_PROPERTY);
		return line.orElse(null);
	}

	private static String describeChildWithLine(Node childNode) {
		String text = textOf(childNode);
		Optional<Integer> lineStart = childNode.getProperty(LINE_START_PROPERTY);
		Optional<Integer> lineEnd = childNode.getProperty(LINE_END_PROPERTY);
		if (lineStart.isPresent() && lineEnd.isPresent()) {
			text += lineStart.equals(lineEnd) ? " (line " + lineStart.get() + ")" : " (lines " + lineStart.get() + "-" + lineEnd.get() + ")";
		}
		return text;
	}

	/** At most this many characters of a structural node's text ({@link #textOf}). */
	static final int MAX_TEXT = 200;

	/**
	 * The text [node] stands for: its artifact's text - unless that is only an adapter's name for a
	 * structure whose text is in its children, like Markdown's "(paragraph)" or "(bullet list,
	 * marker=-)": then the text of its subtree's leaves, joined by spaces, at most {@link #MAX_TEXT}
	 * characters. What a human (or an LLM, see the reorder dialog) deciding an order needs to read.
	 */
	public static String textOf(Node node) {
		String own = String.valueOf(node.getArtifact());
		if (node.getChildren().isEmpty() || !(own.startsWith("(") && own.endsWith(")")))
			return own;
		StringBuilder leaves = new StringBuilder();
		appendLeaves(node, leaves);
		String text = leaves.toString().replaceAll("\\s+", " ").trim();
		if (text.isEmpty())
			return own;
		return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT - 1) + "\u2026";
	}

	private static void appendLeaves(Node node, StringBuilder leaves) {
		if (leaves.length() > MAX_TEXT)
			return;
		if (node.getChildren().isEmpty()) {
			leaves.append(node.getArtifact()).append(' ');
			return;
		}
		for (Node child : node.getChildren())
			appendLeaves(child, leaves);
	}

	/**
	 * Comma-joined, single-line rendering of {@link #describeChildrenWithLines} -- for the
	 * {@code .warnings} file, which keeps one line per diagnostic. The GUI uses {@link
	 * #describeChildrenWithLines} directly instead, joined with newlines for readability.
	 */
	public static String describeChildren(Node node) {
		return String.join(", ", describeChildrenWithLines(node));
	}

	/**
	 * The ORDER warnings of [orderWarnings] with a decision left (see {@link #describeUndecided}):
	 * those whose order the graph's history fixes for the children present need no action - the
	 * GUI left them out, the .warnings file and the command line counted them.
	 */
	public static List<Node> actionableOrderWarnings(java.util.Collection<? extends Node> orderWarnings) {
		List<Node> actionable = new java.util.ArrayList<>();
		for (Node node : orderWarnings) {
			PartialOrderGraph graph = node.getArtifact() != null ? node.getArtifact().getPartialOrderGraph() : null;
			if (graph == null || graph.hasUnresolvedOrder(node))
				actionable.add(node);
		}
		return actionable;
	}

	/** Characters of a child's text in {@link #describeUndecided}. */
	static final int UNDECIDED_TEXT = 40;

	/**
	 * Where the order of [node]'s children is undecided ({@link at.jku.isse.ecco.pog.OrderRegions#undecided}),
	 * a range each, by its places among the children - lines, when every child is one line - and its
	 * first and last text: "lines 12-14 (int a; … int b;)", "2-3 of 40 (X … Y)". All of the
	 * children without an order graph. The warnings listed every child, the decided ones too. The
	 * lines the adapter recorded are those of the variant a child was committed with, not of the file
	 * checked out: in a file whose order is undecided they are not where the lines are.
	 */
	public static List<String> describeUndecided(Node node) {
		List<? extends Node> children = node.getChildren();
		PartialOrderGraph graph = node.getArtifact() != null ? node.getArtifact().getPartialOrderGraph() : null;
		List<at.jku.isse.ecco.pog.OrderRegions.Range> ranges = graph != null
				? at.jku.isse.ecco.pog.OrderRegions.undecided(graph, node)
				: children.size() > 1 ? List.of(new at.jku.isse.ecco.pog.OrderRegions.Range(0, children.size() - 1)) : List.of();
		// lines: every child with lines recorded is one (a text file's markers, last, have none)
		boolean lines = false;
		for (Node child : children) {
			Integer start = lineStart(child);
			if (start == null)
				continue;
			lines = start.equals(lineEnd(child));
			if (!lines)
				break;
		}
		List<String> described = new java.util.ArrayList<>();
		for (at.jku.isse.ecco.pog.OrderRegions.Range range : ranges) {
			Node first = children.get(range.from()), last = children.get(range.to());
			String where = lines
					? "lines " + (range.from() + 1) + "-" + (range.to() + 1)
					: (range.from() + 1) + "-" + (range.to() + 1) + " of " + children.size();
			described.add(where + " (" + shortText(first) + " \u2026 " + shortText(last) + ")");
		}
		return described;
	}

	private static String shortText(Node node) {
		String text = textOf(node).replaceAll("\\s+", " ").trim();
		return text.length() <= UNDECIDED_TEXT ? text : text.substring(0, UNDECIDED_TEXT - 1) + "\u2026";
	}

	/**
	 * Short guidance for the "Suggested Fix" column/warnings-file line -- distinguishes a genuinely
	 * actionable warning (some pair of {@code orderWarningNode}'s current children still has no fixed
	 * relative order) from one where the graph's history already fully determines this specific set of
	 * children, so there's nothing to actually reorder even though the node was flagged (see {@link
	 * PartialOrderGraph#hasUnresolvedOrder}). Falls back to the always-actionable message when there's
	 * no graph to query at all (should be unreachable for a real ORDER warning, which requires one).
	 */
	public static String suggestOrderFix(Node orderWarningNode) {
		PartialOrderGraph graph = orderWarningNode.getArtifact() != null ? orderWarningNode.getArtifact().getPartialOrderGraph() : null;
		if (graph != null && !graph.hasUnresolvedOrder(orderWarningNode)) {
			return "no action needed -- these children's order is already fully determined by prior commits "
					+ "(this warning comes from other content elsewhere in the version history that isn't part of this composition)";
		}
		return "the order of these children could not be determined from commit history -- "
				+ "put them in order in the checked-out file and run `ecco order <file>`, or use Reorder... in the "
				+ "GUI's checkout details; both record the order without a commit (committing the reordered checkout "
				+ "records it too, but also records this configuration as a variant)";
	}

}
