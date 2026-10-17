package at.jku.isse.ecco.pog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a partial order graph says about an order, for people to read: where the order of a node's
 * children is still undecided ({@link #undecided}), and the graph with its straight runs folded
 * into blocks, laid out in layers from the start ({@link #collapse}). A file's graph has a node per
 * line, and nearly all of it is one straight run: the places where variants order things
 * differently - what there is to see - were lost among thousands of nodes.
 */
public final class OrderRegions {

	private OrderRegions() {
	}

	/** Positions [from, to] of a node's children, 0-based and inclusive. */
	public record Range(int from, int to) {
		public int size() {
			return this.to - this.from + 1;
		}
	}

	/**
	 * The ranges of {@code treeNode}'s children whose order {@code pog} does not fix: each pair of
	 * neighbours it does not order (directly or transitively, {@link PartialOrderGraph.Op#canReach}),
	 * pairs that touch merged - what {@link PartialOrderGraph#hasUnresolvedOrder} asks for. A child
	 * the graph does not know is free next to its neighbours, as there.
	 */
	public static List<Range> undecided(PartialOrderGraph pog, at.jku.isse.ecco.tree.Node treeNode) {
		Map<at.jku.isse.ecco.tree.Node, PartialOrderGraph.Node> matched = pog.matchChildren(treeNode);
		List<? extends at.jku.isse.ecco.tree.Node> children = treeNode.getChildren();
		List<Range> ranges = new ArrayList<>();
		for (int i = 0; i < children.size() - 1; i++) {
			PartialOrderGraph.Node a = matched.get(children.get(i));
			PartialOrderGraph.Node b = matched.get(children.get(i + 1));
			if (a != null && b != null && PartialOrderGraph.Op.canReach(a, b))
				continue;
			Range last = ranges.isEmpty() ? null : ranges.get(ranges.size() - 1);
			if (last != null && last.to() == i)
				ranges.set(ranges.size() - 1, new Range(last.from(), i + 1));
			else
				ranges.add(new Range(i, i + 1));
		}
		return ranges;
	}

	/** The start, the end, a run of nodes - and in an {@link #outline}, what only other variants have between two blocks shown. */
	public enum Kind {START, END, NODES, OTHERS}

	/**
	 * Graph nodes drawn as one: the start, the end, or a straight run - each node of it the only next
	 * one of the one before. {@code shown} if its nodes are in the tree node shown, else they are of
	 * other variants only; {@code branch} if the run begins where alternatives meet or ends where
	 * they part. {@code layer} is its distance from the start along the longest way there,
	 * {@code column} its place in the layer, the shown blocks first.
	 */
	public record Block(Kind kind, List<PartialOrderGraph.Node> nodes, boolean shown, boolean branch, int layer, int column) {
		public PartialOrderGraph.Node first() {
			return this.nodes.get(0);
		}

		public PartialOrderGraph.Node last() {
			return this.nodes.get(this.nodes.size() - 1);
		}
	}

	/** An edge between two blocks, by their indices. */
	public record Edge(int from, int to) {
	}

	public record Collapsed(List<Block> blocks, List<Edge> edges) {
	}

	/**
	 * {@code pog} with its straight runs folded into blocks: a run is split where it passes from nodes
	 * in {@code shown} to nodes not in it, so what other variants have stays apart. Each node once,
	 * each edge between blocks once.
	 */
	public static Collapsed collapse(PartialOrderGraph pog, Set<PartialOrderGraph.Node> shown) {
		List<PartialOrderGraph.Node> order = topologicalOrder(pog);
		Map<PartialOrderGraph.Node, Integer> blockOf = new IdentityHashMap<>();
		List<List<PartialOrderGraph.Node>> members = new ArrayList<>();
		List<Kind> kinds = new ArrayList<>();
		PartialOrderGraph.Node head = pog.getHead();
		for (PartialOrderGraph.Node node : order) {
			Kind kind = node == head ? Kind.START : node.getNext().isEmpty() && node.getArtifact() == null ? Kind.END : Kind.NODES;
			Integer joined = null;
			if (kind == Kind.NODES && node.getPrevious().size() == 1) {
				PartialOrderGraph.Node previous = node.getPrevious().iterator().next();
				Integer previousBlock = blockOf.get(previous);
				if (previousBlock != null && kinds.get(previousBlock) == Kind.NODES && previous.getNext().size() == 1
						&& shown.contains(previous) == shown.contains(node))
					joined = previousBlock;
			}
			if (joined == null) {
				joined = members.size();
				members.add(new ArrayList<>());
				kinds.add(kind);
			}
			members.get(joined).add(node);
			blockOf.put(node, joined);
		}

		Set<Edge> edges = new LinkedHashSet<>();
		for (int b = 0; b < members.size(); b++) {
			List<PartialOrderGraph.Node> nodes = members.get(b);
			for (PartialOrderGraph.Node next : nodes.get(nodes.size() - 1).getNext()) {
				Integer to = blockOf.get(next);
				if (to != null)
					edges.add(new Edge(b, to));
			}
		}

		// blocks are made in topological order of their first nodes: every edge goes forward
		int[] layers = new int[members.size()];
		for (Edge edge : edges)
			layers[edge.to()] = Math.max(layers[edge.to()], layers[edge.from()] + 1);
		List<Integer> byLayer = new ArrayList<>();
		for (int b = 0; b < members.size(); b++)
			byLayer.add(b);
		byLayer.sort((x, y) -> {
			if (layers[x] != layers[y])
				return Integer.compare(layers[x], layers[y]);
			boolean xShown = isShown(members.get(x), shown), yShown = isShown(members.get(y), shown);
			if (xShown != yShown)
				return xShown ? -1 : 1;
			return Integer.compare(x, y);
		});
		int[] columns = new int[members.size()];
		int lastLayer = -1, column = 0;
		for (int b : byLayer) {
			column = layers[b] == lastLayer ? column + 1 : 0;
			lastLayer = layers[b];
			columns[b] = column;
		}

		List<Block> blocks = new ArrayList<>();
		for (int b = 0; b < members.size(); b++) {
			List<PartialOrderGraph.Node> nodes = Collections.unmodifiableList(members.get(b));
			boolean branch = kinds.get(b) == Kind.NODES
					&& (nodes.get(0).getPrevious().size() > 1 || nodes.get(nodes.size() - 1).getNext().size() > 1);
			blocks.add(new Block(kinds.get(b), nodes, isShown(nodes, shown), branch, layers[b], columns[b]));
		}
		return new Collapsed(blocks, List.copyOf(edges));
	}

	/**
	 * [collapsed] as an outline: its blocks of shown nodes, the start and the end, one after another
	 * in one column, and the blocks only other variants have between two of them folded into one of
	 * kind {@link Kind#OTHERS}. A Git import's file holds every line it ever had - tinycc's tcc.c,
	 * once the whole compiler, 23,799 nodes for 161 children today - and the lines shown were a
	 * thread through thousands of blocks of history. The kept blocks keep their order: collapse makes
	 * them in the order of the graph, in which the nodes shown come as they are shown.
	 */
	public static Collapsed outline(Collapsed collapsed) {
		List<Block> all = collapsed.blocks();
		// each block of other variants goes before the first kept block it leads to: alternatives to a
		// block shown come between it and the next one - the order of the graph alone put them on
		// either side of it
		List<List<Integer>> next = new ArrayList<>();
		for (int b = 0; b < all.size(); b++)
			next.add(new ArrayList<>());
		for (Edge edge : collapsed.edges())
			next.get(edge.from()).add(edge.to());
		int[] leadsTo = new int[all.size()];
		for (int b = all.size() - 1; b >= 0; b--) {
			leadsTo[b] = Integer.MAX_VALUE;
			for (int n : next.get(b))
				leadsTo[b] = Math.min(leadsTo[b], kept(all.get(n)) ? n : leadsTo[n]);
		}
		Map<Integer, List<PartialOrderGraph.Node>> before = new java.util.HashMap<>();
		List<PartialOrderGraph.Node> atTheEnd = new ArrayList<>();
		for (int b = 0; b < all.size(); b++)
			if (!kept(all.get(b)))
				(leadsTo[b] == Integer.MAX_VALUE ? atTheEnd : before.computeIfAbsent(leadsTo[b], k -> new ArrayList<>())).addAll(all.get(b).nodes());

		List<Block> blocks = new ArrayList<>();
		for (int b = 0; b < all.size(); b++) {
			Block block = all.get(b);
			if (!kept(block))
				continue;
			List<PartialOrderGraph.Node> others = before.get(b);
			if (others != null)
				blocks.add(new Block(Kind.OTHERS, List.copyOf(others), false, false, blocks.size(), 0));
			blocks.add(new Block(block.kind(), block.nodes(), block.shown(), block.branch(), blocks.size(), 0));
		}
		if (!atTheEnd.isEmpty())
			blocks.add(new Block(Kind.OTHERS, List.copyOf(atTheEnd), false, false, blocks.size(), 0));
		List<Edge> edges = new ArrayList<>();
		for (int b = 1; b < blocks.size(); b++)
			edges.add(new Edge(b - 1, b));
		return new Collapsed(blocks, edges);
	}

	/** In an outline as it is: the start, the end and the blocks shown. */
	private static boolean kept(Block block) {
		return block.kind() != Kind.NODES || block.shown();
	}

	private static boolean isShown(List<PartialOrderGraph.Node> nodes, Set<PartialOrderGraph.Node> shown) {
		return shown.contains(nodes.get(0));
	}

	/** Every node reachable from the head once, each after all its previous ones reachable from there. */
	private static List<PartialOrderGraph.Node> topologicalOrder(PartialOrderGraph pog) {
		Set<PartialOrderGraph.Node> reachable = Collections.newSetFromMap(new IdentityHashMap<>());
		Deque<PartialOrderGraph.Node> pending = new ArrayDeque<>();
		reachable.add(pog.getHead());
		pending.push(pog.getHead());
		while (!pending.isEmpty())
			for (PartialOrderGraph.Node next : pending.pop().getNext())
				if (reachable.add(next))
					pending.push(next);

		Map<PartialOrderGraph.Node, Integer> waiting = new IdentityHashMap<>();
		for (PartialOrderGraph.Node node : reachable) {
			int previous = 0;
			for (PartialOrderGraph.Node p : node.getPrevious())
				if (reachable.contains(p))
					previous++;
			waiting.put(node, previous);
		}
		List<PartialOrderGraph.Node> order = new ArrayList<>(reachable.size());
		Deque<PartialOrderGraph.Node> ready = new ArrayDeque<>();
		ready.add(pog.getHead());
		while (!ready.isEmpty()) {
			PartialOrderGraph.Node node = ready.poll();
			order.add(node);
			for (PartialOrderGraph.Node next : node.getNext())
				if (waiting.merge(next, -1, Integer::sum) == 0)
					ready.add(next);
		}
		return order;
	}
}
