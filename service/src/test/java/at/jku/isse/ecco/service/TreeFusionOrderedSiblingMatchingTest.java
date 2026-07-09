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
 * Characterizes a real checkout-correctness bug found via a real user repository (not included
 * here - see memory/lytiny-treefusion-duplicate-token-bug for the original investigation):
 * {@code Trees.treeFusion()} (used to build the cached mainTree every real checkout reads from,
 * via {@code SerBoostedAssociationMerger.buildMainTree()} - NOT the same path as
 * {@code Trees.merge()}/{@code LazyCompositionRootNode}, which are GUI-preview-only) can misdirect
 * content into the wrong sibling when a parent has multiple content-equal ordered children - a
 * real, necessary pattern for adapters like lilypond, which model structurally-similar-but-
 * logically-distinct constructs (e.g. each voice's clef-value wrapper) as ordered siblings sharing
 * the same generic context type.
 * <p>
 * Root cause: {@code SerNode.getEqualChild()} matches purely by
 * {@code child.getArtifact().equals(template.getArtifact())} - the first content-matching child
 * wins, with no way to account for WHICH occurrence a template is meant to correspond to. This is
 * fine when only one candidate exists, but {@code SerNode.addChild()} explicitly requires a parent
 * to be marked "ordered" specifically to allow MULTIPLE content-equal children to coexist as
 * siblings at all (confirmed here: constructing this scenario with a non-ordered parent throws
 * "An equivalent child is already contained" immediately) - meaning ordered-sibling ambiguity is
 * not a rare edge case, it's the very case "ordered" exists to support, and getEqualChild() was
 * never adapted to disambiguate within it.
 * <p>
 * Deliberately not fixed - this is core {@code Trees}/compose logic shared by every checkout,
 * across every backend and adapter; a real fix needs to disambiguate ordered-sibling matches
 * without breaking every other (single-candidate) case that already works correctly today.
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
	public void treeFusion_misdirectsContent_whenAlreadyFilledSlotComesFirst() {
		// same scenario as the test above, but the ALREADY-FILLED slot happens to be first in
		// child order instead of the empty one - the only thing that differs between this test and
		// the one above is sibling ORDER, yet the outcome is completely different: this is not a
		// content-dependent bug, it's a positional one
		Node.Op mainTree = orderedContext("LilyPond.musiclist");
		Node.Op altoSlot = withChild(context("LilyPond.list"), token("treble")); // already filled
		Node.Op sopranoSlot = context("LilyPond.list"); // empty - should have been filled instead
		mainTree.addChild(altoSlot);
		mainTree.addChild(sopranoSlot);

		Node.Op orphan = withChild(orderedContext("LilyPond.musiclist"),
				withChild(context("LilyPond.list"), token("treble")));

		Trees.treeFusion(mainTree, orphan);

		assertEquals(0, sopranoSlot.getChildren().size(),
				"BUG: soprano's slot was never filled - the orphan's content was misdirected into alto's slot instead");
		assertEquals(1, altoSlot.getChildren().size(),
				"alto's slot silently absorbed the orphan (its content happened to already match, " +
						"masking the misdirection here - see the next test for the case where it doesn't)");
	}

	@Test
	public void treeFusion_duplicatesContent_whenMisdirectedIntoADifferentlyPopulatedSlot() {
		// same positional bug as above, but the wrongly-matched slot already holds DIFFERENT
		// content ("treble_8", not "treble") - so instead of the orphan silently vanishing into an
		// existing match, it gets APPENDED as extra, duplicate content: the exact "misplaced
		// duplicate token" symptom seen in the real repository this was characterized from
		Node.Op mainTree = orderedContext("LilyPond.musiclist");
		Node.Op tenorSlot = withChild(context("LilyPond.list"), token("treble_8")); // different content
		Node.Op sopranoSlot = context("LilyPond.list"); // empty - should have been filled instead
		mainTree.addChild(tenorSlot);
		mainTree.addChild(sopranoSlot);

		Node.Op orphan = withChild(orderedContext("LilyPond.musiclist"),
				withChild(context("LilyPond.list"), token("treble")));

		Trees.treeFusion(mainTree, orphan);

		assertEquals(0, sopranoSlot.getChildren().size(),
				"BUG: soprano's slot was never filled");
		assertEquals(2, tenorSlot.getChildren().size(),
				"BUG: tenor's slot now incorrectly holds both its own \"treble_8\" AND the " +
						"misdirected \"treble\" - genuine duplicated/garbled content, not just a silent loss");
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
