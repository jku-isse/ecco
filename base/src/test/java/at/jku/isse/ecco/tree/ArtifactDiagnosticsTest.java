package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraphNode;
import at.jku.isse.ecco.storage.ser.tree.SerNode;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ArtifactDiagnostics} renders {@code ORDER} checkout diagnostics -- a real, small hand-built
 * tree (root -> "File" -> "Parent" -> ["X", "Y"]), no mocking, matching this codebase's established
 * real-fixture testing style for base-level diagnostics rendering (see {@code ModuleRevisionsTest}).
 */
public class ArtifactDiagnosticsTest {

	@Test
	public void describePath_rendersBreadcrumbFromRootToArtifact() {
		SerNode root = new SerNode();

		Artifact.Op<?> fileArtifact = new SerArtifact<>(new TestArtifactData("File"));
		SerNode fileNode = new SerNode(fileArtifact);
		root.addChild(fileNode);

		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		SerNode parentNode = new SerNode(parentArtifact);
		fileNode.addChild(parentNode);

		assertEquals("File > Parent", ArtifactDiagnostics.describePath(parentNode));
	}

	/** An adapter's name for a structure, like Markdown's "(paragraph)", is shown as the text of its leaves. */
	@Test
	public void textOf_aStructuresLabelIsReplacedByItsLeavesText() {
		SerNode paragraph = new SerNode(new SerArtifact<>(new TestArtifactData("(paragraph)")));
		paragraph.addChild(new SerNode(new SerArtifact<>(new TestArtifactData("A paragraph"))));
		paragraph.addChild(new SerNode(new SerArtifact<>(new TestArtifactData("  about apples."))));
		assertEquals("A paragraph about apples.", ArtifactDiagnostics.textOf(paragraph));

		SerNode line = new SerNode(new SerArtifact<>(new TestArtifactData("int x = 1;")));
		line.addChild(new SerNode(new SerArtifact<>(new TestArtifactData("child"))));
		assertEquals("int x = 1;", ArtifactDiagnostics.textOf(line), "a text of its own stays");
		assertEquals("(final newline)", ArtifactDiagnostics.textOf(new SerNode(new SerArtifact<>(new TestArtifactData("(final newline)")))), "no children: the label");

		SerNode long_ = new SerNode(new SerArtifact<>(new TestArtifactData("(paragraph)")));
		for (int i = 0; i < 100; i++)
			long_.addChild(new SerNode(new SerArtifact<>(new TestArtifactData("word" + i))));
		String text = ArtifactDiagnostics.textOf(long_);
		assertEquals(ArtifactDiagnostics.MAX_TEXT, text.length());
		assertTrue(text.endsWith("\u2026"));
	}

	@Test
	public void describeChildren_rendersCurrentChildOrder() {
		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		SerNode parentNode = new SerNode(parentArtifact);

		Artifact.Op<?> childX = new SerArtifact<>(new TestArtifactData("X"));
		SerNode childNodeX = new SerNode(childX);
		parentNode.addChild(childNodeX);

		Artifact.Op<?> childY = new SerArtifact<>(new TestArtifactData("Y"));
		SerNode childNodeY = new SerNode(childY);
		parentNode.addChild(childNodeY);

		assertEquals("X, Y", ArtifactDiagnostics.describeChildren(parentNode));
	}

	@Test
	public void describeChildrenWithLines_includesLineNumbersWhenTheAdapterTrackedThem() {
		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		SerNode parentNode = new SerNode(parentArtifact);

		// matches the "LINE_START"/"LINE_END" node-property convention used by the text and
		// lilypond adapters (TextReader.PROPERTY_LINE_START/PROPERTY_LINE_END).
		Artifact.Op<?> childX = new SerArtifact<>(new TestArtifactData("X"));
		SerNode childNodeX = new SerNode(childX);
		childNodeX.putProperty("LINE_START", 2);
		childNodeX.putProperty("LINE_END", 2);
		parentNode.addChild(childNodeX);

		Artifact.Op<?> childY = new SerArtifact<>(new TestArtifactData("Y"));
		SerNode childNodeY = new SerNode(childY);
		childNodeY.putProperty("LINE_START", 3);
		childNodeY.putProperty("LINE_END", 4);
		parentNode.addChild(childNodeY);

		assertEquals(java.util.List.of("X (line 2)", "Y (lines 3-4)"), ArtifactDiagnostics.describeChildrenWithLines(parentNode));
		assertEquals("X (line 2), Y (lines 3-4)", ArtifactDiagnostics.describeChildren(parentNode));
	}

	@Test
	public void describeChildrenWithLines_omitsLineAnnotationWhenNotTracked() {
		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		SerNode parentNode = new SerNode(parentArtifact);

		Artifact.Op<?> childX = new SerArtifact<>(new TestArtifactData("X"));
		SerNode childNodeX = new SerNode(childX);
		parentNode.addChild(childNodeX);

		assertEquals(java.util.List.of("X"), ArtifactDiagnostics.describeChildrenWithLines(parentNode));
	}

	@Test
	public void describeChildrenWithLines_explainsRatherThanShowingBlank_whenNodeHasNoChildren() {
		// a genuinely order-ambiguous node has >= 2 children by definition, but the rendering stays
		// defensive against 0 children rather than silently showing a blank/misleading "()".
		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		SerNode parentNode = new SerNode(parentArtifact);

		assertEquals(java.util.List.of("(unable to determine current children -- check the checked-out file directly)"),
				ArtifactDiagnostics.describeChildrenWithLines(parentNode));
	}

	@Test
	public void suggestOrderFix_withNoGraph_suggestsManualReorder() {
		// no PartialOrderGraph attached at all - should be unreachable for a real ORDER warning, but
		// falls back to the always-actionable message rather than claiming certainty it doesn't have.
		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		SerNode parentNode = new SerNode(parentArtifact);

		String suggestion = ArtifactDiagnostics.suggestOrderFix(parentNode);

		assertTrue(suggestion.contains("commit"));
	}

	@Test
	public void suggestOrderFix_withGenuineBranchPointAmongCurrentChildren_suggestsManualReorder() {
		Artifact.Op<?> childX = new SerArtifact<>(new TestArtifactData("X"));
		Artifact.Op<?> childY = new SerArtifact<>(new TestArtifactData("Y"));

		// head -> {X, Y} -> tail: a genuine, unresolved branch point between the two present children.
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.getHead().removeChild(pog.getTail());
		SerPartialOrderGraphNode pogNodeX = new SerPartialOrderGraphNode(childX);
		SerPartialOrderGraphNode pogNodeY = new SerPartialOrderGraphNode(childY);
		pogNodeX.setSequenceNumber(1);
		pogNodeY.setSequenceNumber(2);
		pog.getHead().addChild(pogNodeX);
		pog.getHead().addChild(pogNodeY);
		pogNodeX.addChild(pog.getTail());
		pogNodeY.addChild(pog.getTail());

		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		parentArtifact.setPartialOrderGraph(pog);
		SerNode parentNode = new SerNode(parentArtifact);
		parentNode.addChild(new SerNode(childX));
		parentNode.addChild(new SerNode(childY));

		String suggestion = ArtifactDiagnostics.suggestOrderFix(parentNode);

		assertTrue(suggestion.contains("commit"));
	}

	@Test
	public void suggestOrderFix_withOnlyUnrelatedBranchPointElsewhere_saysNoActionNeeded() {
		Artifact.Op<?> childX = new SerArtifact<>(new TestArtifactData("X"));
		Artifact.Op<?> childY = new SerArtifact<>(new TestArtifactData("Y"));
		Artifact.Op<?> otherVariantContent = new SerArtifact<>(new TestArtifactData("OtherVariant"));

		// head -> X -> {Y, OtherVariantContent} -> tail: X/Y is fully fixed; the only genuine branch
		// point is between Y and content from a variant not present in this composition at all.
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.getHead().removeChild(pog.getTail());
		SerPartialOrderGraphNode pogNodeX = new SerPartialOrderGraphNode(childX);
		SerPartialOrderGraphNode pogNodeY = new SerPartialOrderGraphNode(childY);
		SerPartialOrderGraphNode pogNodeOther = new SerPartialOrderGraphNode(otherVariantContent);
		pogNodeX.setSequenceNumber(1);
		pogNodeY.setSequenceNumber(2);
		pogNodeOther.setSequenceNumber(3);
		pog.getHead().addChild(pogNodeX);
		pogNodeX.addChild(pogNodeY);
		pogNodeX.addChild(pogNodeOther);
		pogNodeY.addChild(pog.getTail());
		pogNodeOther.addChild(pog.getTail());

		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		parentArtifact.setPartialOrderGraph(pog);
		SerNode parentNode = new SerNode(parentArtifact);
		// only X and Y are actually present in this composition - OtherVariantContent isn't a child here.
		parentNode.addChild(new SerNode(childX));
		parentNode.addChild(new SerNode(childY));

		String suggestion = ArtifactDiagnostics.suggestOrderFix(parentNode);

		assertTrue(suggestion.contains("no action needed"));
	}


	/**
	 * The undecided ranges of a node's children are named by their lines (or places) and first and
	 * last text; a warning without any is no action and left out - head -> X -> {Y, Z} -> tail with
	 * the children X, Y, Z: Y and Z are undecided.
	 */
	@Test
	public void describeUndecided_namesTheRangesAndActionableLeavesOutTheMootOnes() {
		Artifact.Op<?> childX = new SerArtifact<>(new TestArtifactData("X"));
		Artifact.Op<?> childY = new SerArtifact<>(new TestArtifactData("Y"));
		Artifact.Op<?> childZ = new SerArtifact<>(new TestArtifactData("Z"));
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.getHead().removeChild(pog.getTail());
		SerPartialOrderGraphNode x = new SerPartialOrderGraphNode(childX), y = new SerPartialOrderGraphNode(childY), z = new SerPartialOrderGraphNode(childZ);
		x.setSequenceNumber(1);
		y.setSequenceNumber(2);
		z.setSequenceNumber(3);
		pog.getHead().addChild(x);
		x.addChild(y);
		x.addChild(z);
		y.addChild(pog.getTail());
		z.addChild(pog.getTail());

		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		parentArtifact.setPartialOrderGraph(pog);
		SerNode parent = new SerNode(parentArtifact);
		for (Artifact.Op<?> child : java.util.List.of(childX, childY, childZ))
			parent.addChild(new SerNode(child));
		assertEquals(java.util.List.of("2-3 of 3 (Y \u2026 Z)"), ArtifactDiagnostics.describeUndecided(parent));

		// every child one line: its place is its line in the file checked out (the lines recorded are
		// those of the variant it was committed with)
		for (int i = 0; i < 3; i++) {
			((SerNode) parent.getChildren().get(i)).putProperty("LINE_START", 10 + 3 * i);
			((SerNode) parent.getChildren().get(i)).putProperty("LINE_END", 10 + 3 * i);
		}
		assertEquals(java.util.List.of("lines 2-3 (Y \u2026 Z)"), ArtifactDiagnostics.describeUndecided(parent));
		// one of several lines: places
		((SerNode) parent.getChildren().get(2)).putProperty("LINE_END", 20);
		assertEquals(java.util.List.of("2-3 of 3 (Y \u2026 Z)"), ArtifactDiagnostics.describeUndecided(parent));

		// X, Y only: decided - no action, left out
		SerNode decided = new SerNode(parentArtifact);
		decided.addChild(new SerNode(childX));
		decided.addChild(new SerNode(childY));
		assertEquals(java.util.List.of(), ArtifactDiagnostics.describeUndecided(decided));
		assertEquals(java.util.List.of(parent), ArtifactDiagnostics.actionableOrderWarnings(java.util.List.of(parent, decided)));
		// all at once, the ranges found once: the warnings with a decision left, where, and what to do
		java.util.List<ArtifactDiagnostics.OrderWarning> warnings = ArtifactDiagnostics.describeOrderWarnings(java.util.List.of(parent, decided));
		assertEquals(1, warnings.size());
		assertEquals(parent, warnings.get(0).node());
		assertEquals(ArtifactDiagnostics.describeUndecided(parent), warnings.get(0).undecided());
		assertEquals(ArtifactDiagnostics.suggestOrderFix(parent), warnings.get(0).suggestedFix());

		// without a graph all of it is undecided
		SerNode noGraph = new SerNode(new SerArtifact<>(new TestArtifactData("P")));
		noGraph.addChild(new SerNode(new SerArtifact<>(new TestArtifactData("A"))));
		noGraph.addChild(new SerNode(new SerArtifact<>(new TestArtifactData("B"))));
		assertEquals(java.util.List.of("1-2 of 2 (A \u2026 B)"), ArtifactDiagnostics.describeUndecided(noGraph));
		assertEquals(java.util.List.of(noGraph), ArtifactDiagnostics.actionableOrderWarnings(java.util.List.of(noGraph)));
	}

}
