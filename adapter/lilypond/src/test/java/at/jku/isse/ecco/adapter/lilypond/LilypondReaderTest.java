package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.data.ContextArtifactDataFactory;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises {@link LilypondReader#generateEccoTree}, including the hardened case: a level drop
 * bigger than the ecco-tree's own remaining depth, which used to NPE (see the class's own SEVERE
 * log message, added alongside the fix). */
public class LilypondReaderTest {

	private final LilypondReader reader = new LilypondReader(new SerEntityFactory());
	private final SerEntityFactory ef = new SerEntityFactory();

	@Test
	void generateEccoTreeBuildsNestedOrderedNodesFromIncreasingLevelsAndSiblingsFromDrops() {
		Node.Op root = ef.createOrderedNode(ef.createArtifact(ContextArtifactDataFactory.getContextArtifactData("root")));

		LilypondNode<ParceToken> n1 = new LilypondNode<>("ctx", null);
		n1.setLevel(0);
		LilypondNode<ParceToken> n2 = n1.append("tok", new ParceToken(0, "hello", "Token"), 1);
		n2.append("ctx2", null, 0);

		reader.generateEccoTree(n1, root);

		assertEquals(2, root.getChildren().size());

		Node ctxChild = root.getChildren().get(0);
		assertEquals("DefaultContextArtifactData", ctxChild.getArtifact().getData().getClass().getSimpleName());
		assertEquals(1, ctxChild.getChildren().size());

		Node tokChild = ctxChild.getChildren().get(0);
		assertEquals("hello", ((DefaultTokenArtifactData) tokChild.getArtifact().getData()).getText());

		Node ctx2Child = root.getChildren().get(1);
		assertTrue(ctx2Child.getChildren().isEmpty());
	}

	@Test
	void generateEccoTreeStopsInsteadOfThrowingWhenALevelDropExceedsTheTreesOwnDepth() {
		// a parentless root, matching the real pluginNode read() passes in - its own getParent()
		// is null, so a level drop demanding more unwinding than that gives an "already null node"
		// scenario one step in, which used to NPE at Node$Op.getParent()
		Node.Op root = ef.createOrderedNode(ef.createArtifact(ContextArtifactDataFactory.getContextArtifactData("root")));

		LilypondNode<ParceToken> n1 = new LilypondNode<>("n1", null);
		n1.setLevel(5);
		n1.append("n2", null, 0); // an oversized drop (5 -> 0) with only the root to unwind to

		assertDoesNotThrow(() -> reader.generateEccoTree(n1, root));

		// construction stopped as soon as it ran out of ancestors to unwind to - n2 never got added
		assertEquals(1, root.getChildren().size());
	}
}
