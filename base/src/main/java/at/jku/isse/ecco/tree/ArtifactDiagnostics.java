package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.artifact.Artifact;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only rendering for {@code ORDER} checkout diagnostics ({@code Checkout#getOrderWarnings()}):
 * artifacts whose children had more than one valid relative order during composition, where {@code
 * DefaultOrderSelector} picked one arbitrarily. Purely a traversal of the already-composed tree --
 * no mutation, no alternative-order enumeration (that needs {@code PartialOrderGraph}'s
 * factorial-blowup-prone linearization methods, deliberately avoided here).
 */
public final class ArtifactDiagnostics {

	private ArtifactDiagnostics() {
	}

	/**
	 * Breadcrumb path from root to the artifact, e.g. {@code "parent > artifact"} -- extracted
	 * verbatim from {@code EccoService.checkout()}'s original inline ORDER-line-building loop,
	 * unchanged behavior.
	 */
	public static String describePath(Artifact<?> artifact) {
		List<String> pathList = new LinkedList<>();
		Node current = artifact.getContainingNode().getParent();
		while (current != null) {
			if (current.getArtifact() != null)
				pathList.add(0, current.getArtifact().toString() + " > ");
			current = current.getParent();
		}
		pathList.add(artifact.toString());
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
	 * The ambiguous artifact's current children in their current (arbitrarily-picked) order, one
	 * entry per child, each annotated with its source line range when the adapter tracked one (e.g.
	 * {@code "X (line 2)"}, or just {@code "X"} if unavailable) -- a read-only reference for a human
	 * deciding whether/how to reorder them.
	 *
	 * <p>{@code Artifact#getContainingNode()} is a single-valued field that can be shared/reused
	 * across more than one tree position during composition (see the
	 * {@code pog-merge-shared-artifact-bug} class of issue) -- for a heavily-fused artifact it can
	 * point at a node from a different context than the one actually in the current checkout's
	 * composed tree, e.g. one with no children at all despite this artifact genuinely being
	 * order-ambiguous. Rather than silently render a blank/misleading "()" in that case, say so.
	 */
	public static List<String> describeChildrenWithLines(Artifact<?> artifact) {
		List<String> children = artifact.getContainingNode().getChildren().stream()
				.map(ArtifactDiagnostics::describeChildWithLine)
				.collect(Collectors.toList());
		if (children.isEmpty()) {
			return List.of("(unable to determine current children -- check the checked-out file directly)");
		}
		return children;
	}

	private static String describeChildWithLine(Node childNode) {
		String text = String.valueOf(childNode.getArtifact());
		Optional<Integer> lineStart = childNode.getProperty(LINE_START_PROPERTY);
		Optional<Integer> lineEnd = childNode.getProperty(LINE_END_PROPERTY);
		if (lineStart.isPresent() && lineEnd.isPresent()) {
			text += lineStart.equals(lineEnd) ? " (line " + lineStart.get() + ")" : " (lines " + lineStart.get() + "-" + lineEnd.get() + ")";
		}
		return text;
	}

	/**
	 * Comma-joined, single-line rendering of {@link #describeChildrenWithLines} -- for the
	 * {@code .warnings} file, which keeps one line per diagnostic. The GUI uses {@link
	 * #describeChildrenWithLines} directly instead, joined with newlines for readability.
	 */
	public static String describeChildren(Artifact<?> artifact) {
		return String.join(", ", describeChildrenWithLines(artifact));
	}

	public static String suggestOrderFix() {
		return "the order of these children could not be determined from commit history -- "
				+ "manually reorder them in the checked-out file if needed, then commit to establish a precedent";
	}

}
