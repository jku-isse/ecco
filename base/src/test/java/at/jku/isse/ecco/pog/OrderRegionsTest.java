package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraphNode;
import at.jku.isse.ecco.storage.ser.tree.SerNode;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OrderRegions}: a graph's straight runs folded into blocks in layers, and the undecided
 * ranges of a node's children - real graphs, merged from the lines of variants as commits do.
 */
public class OrderRegionsTest {

	private static List<Artifact.Op<?>> artifacts(List<String> lines) {
		List<Artifact.Op<?>> artifacts = new ArrayList<>();
		for (String line : lines)
			artifacts.add(new SerArtifact<>(new TestArtifactData(line)));
		return artifacts;
	}

	private static List<String> texts(OrderRegions.Block block) {
		return block.nodes().stream().map(n -> String.valueOf(n.getArtifact())).toList();
	}

	private static Set<PartialOrderGraph.Node> all(PartialOrderGraph pog) {
		Set<PartialOrderGraph.Node> nodes = Collections.newSetFromMap(new IdentityHashMap<>());
		nodes.addAll(pog.collectNodes());
		return nodes;
	}

	/** A file without alternatives - 1,000 lines - is Start, one block of them, End. */
	@Test
	public void aStraightGraphIsStartOneBlockAndEnd() {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < 1000; i++)
			lines.add("line " + i);
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.merge(artifacts(lines));

		OrderRegions.Collapsed collapsed = OrderRegions.collapse(pog, all(pog));

		assertEquals(List.of(OrderRegions.Kind.START, OrderRegions.Kind.NODES, OrderRegions.Kind.END),
				collapsed.blocks().stream().map(OrderRegions.Block::kind).toList());
		assertEquals(1000, collapsed.blocks().get(1).nodes().size());
		assertEquals("line 0", String.valueOf(collapsed.blocks().get(1).first().getArtifact()));
		assertEquals("line 999", String.valueOf(collapsed.blocks().get(1).last().getArtifact()));
		assertEquals(List.of(new OrderRegions.Edge(0, 1), new OrderRegions.Edge(1, 2)), collapsed.edges());
		assertEquals(List.of(0, 1, 2), collapsed.blocks().stream().map(OrderRegions.Block::layer).toList());
		assertFalse(collapsed.blocks().get(1).branch());
	}

	/**
	 * Two variants differing in one place: the shared lines before and after are a block each, the
	 * alternatives a block each, side by side in one layer - the shown one first - between them.
	 */
	@Test
	public void alternativesAreBlocksSideBySideBetweenWhereTheyPartAndMeet() {
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.merge(artifacts(List.of("a1", "a2", "x", "z1", "z2")));
		pog.merge(artifacts(List.of("a1", "a2", "y1", "y2", "z1", "z2")));
		// shown: the variant with y
		Set<PartialOrderGraph.Node> shown = all(pog);
		shown.removeIf(n -> "x".equals(String.valueOf(n.getArtifact())));

		OrderRegions.Collapsed collapsed = OrderRegions.collapse(pog, shown);

		List<List<String>> blocks = collapsed.blocks().stream().map(OrderRegionsTest::texts).toList();
		assertEquals(5 + 1, blocks.size(), blocks.toString()); // start, a1 a2, x, y1 y2, z1 z2, end
		OrderRegions.Block before = block(collapsed, "a1"), x = block(collapsed, "x"), y = block(collapsed, "y1"), after = block(collapsed, "z1");
		assertEquals(List.of("a1", "a2"), texts(before));
		assertEquals(List.of("y1", "y2"), texts(y));
		assertEquals(List.of("z1", "z2"), texts(after));
		assertTrue(before.branch(), "the alternatives part after it");
		assertTrue(after.branch(), "they meet before it");
		assertFalse(y.branch());
		assertEquals(x.layer(), y.layer(), "side by side");
		assertEquals(0, y.column(), "the shown one first");
		assertEquals(1, x.column());
		assertTrue(y.shown());
		assertFalse(x.shown(), "only in other variants");
		assertEquals(before.layer() + 1, y.layer());
		assertEquals(y.layer() + 1, after.layer());
		assertEquals(collapsed.edges().size(), collapsed.edges().stream().distinct().count(), "each edge once");
	}

	/** A run is split where it passes from the lines shown to lines of other variants only. */
	@Test
	public void aRunIsSplitWhereItLeavesTheShownLines() {
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.merge(artifacts(List.of("a", "b", "c", "d")));
		Set<PartialOrderGraph.Node> shown = all(pog);
		shown.removeIf(n -> List.of("c", "d").contains(String.valueOf(n.getArtifact())));

		OrderRegions.Collapsed collapsed = OrderRegions.collapse(pog, shown);

		assertEquals(List.of(List.of("null"), List.of("a", "b"), List.of("c", "d"), List.of("null")),
				collapsed.blocks().stream().map(OrderRegionsTest::texts).toList());
		assertTrue(collapsed.blocks().get(1).shown());
		assertFalse(collapsed.blocks().get(2).shown());
	}

	private static OrderRegions.Block block(OrderRegions.Collapsed collapsed, String firstText) {
		return collapsed.blocks().stream().filter(b -> firstText.equals(String.valueOf(b.first().getArtifact()))).findFirst().orElseThrow();
	}

	/**
	 * The undecided ranges of a node's children: neighbours the graph does not order, touching pairs
	 * merged - head -> X -> {Y, Z} -> W -> tail with the children X, Y, Z, W: Y and Z are undecided.
	 */
	@Test
	public void theUndecidedRangesAreTheNeighboursTheGraphDoesNotOrder() {
		Artifact.Op<?> x = new SerArtifact<>(new TestArtifactData("X"));
		Artifact.Op<?> y = new SerArtifact<>(new TestArtifactData("Y"));
		Artifact.Op<?> z = new SerArtifact<>(new TestArtifactData("Z"));
		Artifact.Op<?> w = new SerArtifact<>(new TestArtifactData("W"));
		SerPartialOrderGraph pog = new SerPartialOrderGraph();
		pog.getHead().removeChild(pog.getTail());
		SerPartialOrderGraphNode nx = node(x, 1), ny = node(y, 2), nz = node(z, 3), nw = node(w, 4);
		pog.getHead().addChild(nx);
		nx.addChild(ny);
		nx.addChild(nz);
		ny.addChild(nw);
		nz.addChild(nw);
		nw.addChild(pog.getTail());

		SerNode parent = new SerNode(new SerArtifact<>(new TestArtifactData("Parent")));
		for (Artifact.Op<?> child : List.of(x, y, z, w))
			parent.addChild(new SerNode(child));

		assertEquals(List.of(new OrderRegions.Range(1, 2)), OrderRegions.undecided(pog, parent));
		assertTrue(pog.hasUnresolvedOrder(parent));

		// all fixed: X Y W - none
		SerNode fixed = new SerNode(new SerArtifact<>(new TestArtifactData("Parent")));
		for (Artifact.Op<?> child : List.of(x, y, w))
			fixed.addChild(new SerNode(child));
		assertEquals(List.of(), OrderRegions.undecided(pog, fixed));
		assertFalse(pog.hasUnresolvedOrder(fixed));

		// two unknown children in a row: one range over the three positions
		SerNode unknown = new SerNode(new SerArtifact<>(new TestArtifactData("Parent")));
		for (String text : List.of("X", "U1", "U2"))
			unknown.addChild(new SerNode(text.equals("X") ? x : new SerArtifact<>(new TestArtifactData(text))));
		assertEquals(List.of(new OrderRegions.Range(0, 2)), OrderRegions.undecided(pog, unknown));
	}

	private static SerPartialOrderGraphNode node(Artifact.Op<?> artifact, int sequenceNumber) {
		SerPartialOrderGraphNode node = new SerPartialOrderGraphNode(artifact);
		node.setSequenceNumber(sequenceNumber);
		return node;
	}
}
