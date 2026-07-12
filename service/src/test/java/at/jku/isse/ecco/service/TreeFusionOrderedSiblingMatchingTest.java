package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.lilypond.data.context.DefaultContextArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Characterizes (and, since {@code Trees.treeFusion()}'s fix, verifies the fix for) a real
 * checkout-correctness bug found via a real user repository (not included here - see
 * memory/lytiny-treefusion-duplicate-token-bug for the original investigation): {@code
 * Trees.treeFusion()} (used to build the cached mainTree every real checkout reads from, via
 * {@code SerBoostedAssociationMerger.buildMainTree()} - NOT the same path as
 * {@code Trees.merge()}/{@code LazyCompositionRootNode}, which are GUI-preview-only) could misdirect
 * content into the wrong sibling when a parent has multiple content-equal ordered children - a
 * real, necessary pattern for adapters like lilypond, which model structurally-similar-but-
 * logically-distinct constructs (e.g. each voice's clef-value wrapper) as ordered siblings sharing
 * the same generic context type.
 * <p>
 * Root cause: {@code Node.getEqualChild()} (see the first test below) matches purely by
 * {@code child.getArtifact().equals(template.getArtifact())} and always returns the FIRST
 * content-matching child, with no way to account for WHICH occurrence a template is meant to
 * correspond to. This is fine when only one candidate exists, but {@code SerNode.addChild()}
 * explicitly requires a parent to be marked "ordered" specifically to allow MULTIPLE content-equal
 * children to coexist as siblings at all (confirmed here: constructing this scenario with a
 * non-ordered parent throws "An equivalent child is already contained" immediately) - meaning
 * ordered-sibling ambiguity is not a rare edge case, it's the very case "ordered" exists to
 * support.
 * <p>
 * Fix: rather than changing {@code getEqualChild()} itself (a general-purpose, single-caller-free
 * interface method whose stateless "first match" semantics other current/future callers could
 * reasonably depend on), {@code Trees.treeFusion()} - its one and only caller - now uses the same
 * {@code ChildIndex} mechanism {@code Trees.slice()} already relies on for matching
 * duplicate/ordered siblings: matched candidates are consumed within one fusion pass (so several
 * content-equal children arriving together each pair up with a DIFFERENT candidate), and among
 * remaining candidates, an empty (not yet filled) one is preferred over an already-filled one -
 * the empty one is far more likely to be the position genuinely still waiting for this content.
 */
public class TreeFusionOrderedSiblingMatchingTest {

	private final SerEntityFactory factory = new SerEntityFactory();

	@Test
	public void addChild_requiresOrderedParent_forMultipleContentEqualSiblings() {
		Node.Op nonOrderedParent = factory.createNode(new DefaultContextArtifactData("LilyPond.list"));
		Node.Op first = factory.createNode(new DefaultContextArtifactData("LilyPond.list"));
		Node.Op second = factory.createNode(new DefaultContextArtifactData("LilyPond.list"));
		nonOrderedParent.addChild(first);

		org.junit.jupiter.api.Assertions.assertThrows(at.jku.isse.ecco.EccoException.class,
				() -> nonOrderedParent.addChild(second),
				"a non-ordered parent should reject a second content-equal child - confirming " +
						"'ordered' is the real system's own mechanism for allowing this, not an " +
						"artificial setup for this test");
	}

	@Test
	public void getEqualChild_alwaysReturnsFirstContentMatch_regardlessOfWhichOneIsCorrect() {
		// this is Node.getEqualChild()'s own, UNCHANGED, low-level behavior - deliberately left
		// alone (see class javadoc for why); Trees.treeFusion() no longer calls it directly for
		// exactly this reason
		Node.Op musiclist = orderedContext("LilyPond.list");
		Node.Op sopranoSlot = context("LilyPond.list");
		Node.Op altoSlot = withChild(context("LilyPond.list"), token("treble"));
		musiclist.addChild(sopranoSlot);
		musiclist.addChild(altoSlot);

		Node.Op match = musiclist.getEqualChild(context("LilyPond.list"));

		assertSame(sopranoSlot, match, "getEqualChild() has no way to distinguish sopranoSlot from " +
				"altoSlot beyond their own (identical) content - it deterministically returns " +
				"whichever comes first in child order, not necessarily the semantically correct one");
	}

	@Test
	public void treeFusion_correctlyFillsEmptySlot_whenItComesFirst() {
		Node.Op mainTree = orderedContext("LilyPond.musiclist");
		Node.Op sopranoSlot = context("LilyPond.list"); // empty - to be filled
		Node.Op altoSlot = withChild(context("LilyPond.list"), token("treble")); // already filled
		mainTree.addChild(sopranoSlot);
		mainTree.addChild(altoSlot);

		Node.Op orphan = withChild(orderedContext("LilyPond.musiclist"),
				withChild(context("LilyPond.list"), token("treble")));

		Trees.treeFusion(mainTree, orphan);

		assertEquals(1, sopranoSlot.getChildren().size(), "soprano's slot should have been filled");
		assertEquals(1, altoSlot.getChildren().size(), "alto's slot should have been left untouched");
	}

	@Test
	public void treeFusion_correctlyFillsEmptySlot_evenWhenAlreadyFilledSlotComesFirst() {
		// same scenario as the test above, but the ALREADY-FILLED slot happens to be first in
		// child order instead of the empty one - before the fix, sibling order alone determined
		// whether this worked; now the outcome is the same regardless of order
		Node.Op mainTree = orderedContext("LilyPond.musiclist");
		Node.Op altoSlot = withChild(context("LilyPond.list"), token("treble")); // already filled
		Node.Op sopranoSlot = context("LilyPond.list"); // empty - should be filled
		mainTree.addChild(altoSlot);
		mainTree.addChild(sopranoSlot);

		Node.Op orphan = withChild(orderedContext("LilyPond.musiclist"),
				withChild(context("LilyPond.list"), token("treble")));

		Trees.treeFusion(mainTree, orphan);

		assertEquals(1, sopranoSlot.getChildren().size(), "soprano's slot should have been filled");
		assertEquals(1, altoSlot.getChildren().size(), "alto's slot should have been left untouched");
	}

	@Test
	public void treeFusion_fillsCorrectSlot_whenWronglyMatchedSlotWouldHaveDuplicatedContent() {
		// same positional scenario as above, but the wrongly-orderable slot already holds DIFFERENT
		// content ("treble_8", not "treble") - before the fix, misdirection here didn't just lose
		// the orphan's content, it actively duplicated content into the wrong slot: the exact
		// "misplaced duplicate token" symptom seen in the real repository this was characterized from
		Node.Op mainTree = orderedContext("LilyPond.musiclist");
		Node.Op tenorSlot = withChild(context("LilyPond.list"), token("treble_8")); // different content
		Node.Op sopranoSlot = context("LilyPond.list"); // empty - should be filled
		mainTree.addChild(tenorSlot);
		mainTree.addChild(sopranoSlot);

		Node.Op orphan = withChild(orderedContext("LilyPond.musiclist"),
				withChild(context("LilyPond.list"), token("treble")));

		Trees.treeFusion(mainTree, orphan);

		assertEquals(1, sopranoSlot.getChildren().size(), "soprano's slot should have been filled");
		assertEquals(1, tenorSlot.getChildren().size(),
				"tenor's slot should have been left with only its own \"treble_8\" - not duplicated");
	}

	@Test
	public void treeFusion_pairsUpMultipleContentEqualSiblings_arrivingInOnePass() {
		// fusionNode itself contributes BOTH voices' clef values in one pass (the common case: a
		// single association's tree already has the full multi-voice structure) - each should pair
		// up with a DIFFERENT mainTree candidate instead of both being funneled into whichever one
		// getEqualChild() would return first
		Node.Op mainTree = orderedContext("LilyPond.musiclist");
		Node.Op sopranoSlot = context("LilyPond.list");
		Node.Op altoSlot = context("LilyPond.list");
		mainTree.addChild(sopranoSlot);
		mainTree.addChild(altoSlot);

		Node.Op fusionNode = orderedContext("LilyPond.musiclist");
		fusionNode.addChild(withChild(context("LilyPond.list"), token("treble")));
		fusionNode.addChild(withChild(context("LilyPond.list"), token("treble")));

		Trees.treeFusion(mainTree, fusionNode);

		assertEquals(1, sopranoSlot.getChildren().size(), "first slot should have received one treble");
		assertEquals(1, altoSlot.getChildren().size(), "second slot should have received the other treble");
	}

	@Test
	public void treeFusion_propagatesNodePropertiesOntoTheKeptSibling_whenAMatchIsFound() {
		// LINE_START/LINE_END (set by adapters like TextReader/LilypondLineReader at parse time) are
		// display-only metadata, not structural -- but were silently dropped for a content-equal
		// sibling reused via mainIndex, unlike feature trace fusion and the PartialOrderGraph merge
		// in the very same branch, which were already propagated correctly. Root-caused via a real
		// GUI bug report: ORDER-warning line numbers were missing in practice despite surviving a
		// real close/reopen round trip for an ordinary (non-fused) checkout.
		Node.Op mainTree = orderedContext("LilyPond.musiclist");
		Node.Op sopranoSlot = context("LilyPond.list");
		mainTree.addChild(sopranoSlot);

		Node.Op incomingSlot = context("LilyPond.list");
		incomingSlot.putProperty("LINE_START", 5);
		incomingSlot.putProperty("LINE_END", 5);
		Node.Op orphan = withChild(orderedContext("LilyPond.musiclist"), incomingSlot);

		Trees.treeFusion(mainTree, orphan);

		assertEquals(java.util.Optional.of(5), sopranoSlot.getProperty("LINE_START"),
				"LINE_START must survive fusion onto the kept sibling, not just the feature trace and PartialOrderGraph");
		assertEquals(java.util.Optional.of(5), sopranoSlot.getProperty("LINE_END"));
	}

	private Node.Op context(String name) {
		return factory.createNode(new DefaultContextArtifactData(name));
	}

	private Node.Op orderedContext(String name) {
		return factory.createOrderedNode(new DefaultContextArtifactData(name));
	}

	private Node.Op token(String text) {
		return factory.createNode(new DefaultTokenArtifactData(new ParceToken(0, "\"" + text + "\"", "Literal.String")));
	}

	private Node.Op withChild(Node.Op parent, Node.Op child) {
		parent.addChild(child);
		return parent;
	}
}
