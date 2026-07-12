package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
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
		fileArtifact.setContainingNode(fileNode);
		root.addChild(fileNode);

		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		SerNode parentNode = new SerNode(parentArtifact);
		parentArtifact.setContainingNode(parentNode);
		fileNode.addChild(parentNode);

		assertEquals("File > Parent", ArtifactDiagnostics.describePath(parentArtifact));
	}

	@Test
	public void describeChildren_rendersCurrentChildOrder() {
		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		SerNode parentNode = new SerNode(parentArtifact);
		parentArtifact.setContainingNode(parentNode);

		Artifact.Op<?> childX = new SerArtifact<>(new TestArtifactData("X"));
		SerNode childNodeX = new SerNode(childX);
		childX.setContainingNode(childNodeX);
		parentNode.addChild(childNodeX);

		Artifact.Op<?> childY = new SerArtifact<>(new TestArtifactData("Y"));
		SerNode childNodeY = new SerNode(childY);
		childY.setContainingNode(childNodeY);
		parentNode.addChild(childNodeY);

		assertEquals("X, Y", ArtifactDiagnostics.describeChildren(parentArtifact));
	}

	@Test
	public void describeChildrenWithLines_includesLineNumbersWhenTheAdapterTrackedThem() {
		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		SerNode parentNode = new SerNode(parentArtifact);
		parentArtifact.setContainingNode(parentNode);

		// matches the "LINE_START"/"LINE_END" node-property convention used by the text and
		// lilypond adapters (TextReader.PROPERTY_LINE_START/PROPERTY_LINE_END).
		Artifact.Op<?> childX = new SerArtifact<>(new TestArtifactData("X"));
		SerNode childNodeX = new SerNode(childX);
		childX.setContainingNode(childNodeX);
		childNodeX.putProperty("LINE_START", 2);
		childNodeX.putProperty("LINE_END", 2);
		parentNode.addChild(childNodeX);

		Artifact.Op<?> childY = new SerArtifact<>(new TestArtifactData("Y"));
		SerNode childNodeY = new SerNode(childY);
		childY.setContainingNode(childNodeY);
		childNodeY.putProperty("LINE_START", 3);
		childNodeY.putProperty("LINE_END", 4);
		parentNode.addChild(childNodeY);

		assertEquals(java.util.List.of("X (line 2)", "Y (lines 3-4)"), ArtifactDiagnostics.describeChildrenWithLines(parentArtifact));
		assertEquals("X (line 2), Y (lines 3-4)", ArtifactDiagnostics.describeChildren(parentArtifact));
	}

	@Test
	public void describeChildrenWithLines_omitsLineAnnotationWhenNotTracked() {
		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		SerNode parentNode = new SerNode(parentArtifact);
		parentArtifact.setContainingNode(parentNode);

		Artifact.Op<?> childX = new SerArtifact<>(new TestArtifactData("X"));
		SerNode childNodeX = new SerNode(childX);
		childX.setContainingNode(childNodeX);
		parentNode.addChild(childNodeX);

		assertEquals(java.util.List.of("X"), ArtifactDiagnostics.describeChildrenWithLines(parentArtifact));
	}

	@Test
	public void describeChildrenWithLines_explainsRatherThanShowingBlank_whenContainingNodeHasNoChildren() {
		// simulates the real-world case where artifact.getContainingNode() points at a node from a
		// different tree context (a shared/reused artifact -- see pog-merge-shared-artifact-bug)
		// than the one actually in the current checkout's composed tree: the parent artifact is
		// genuinely order-ambiguous, but its (stale) containing node here has no children at all.
		Artifact.Op<?> parentArtifact = new SerArtifact<>(new TestArtifactData("Parent"));
		SerNode parentNode = new SerNode(parentArtifact);
		parentArtifact.setContainingNode(parentNode);

		assertEquals(java.util.List.of("(unable to determine current children -- check the checked-out file directly)"),
				ArtifactDiagnostics.describeChildrenWithLines(parentArtifact));
	}

	@Test
	public void suggestOrderFix_isNonEmptyGuidance() {
		String suggestion = ArtifactDiagnostics.suggestOrderFix();

		assertTrue(suggestion.contains("commit"));
	}

}
