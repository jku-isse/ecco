package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.artifact.Artifact;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import java.util.*;

/**
 * The alignment of a sequence with a partial order graph that matches the most artifacts (equal
 * data) over every ordering the graph allows - exact, without listing the orderings.
 * <p>
 * The orderings of a graph pass through its down-sets: sets of nodes that contain, with each node,
 * every node before it. Let best(D, j) be the most matches between an ordering of the down-set D and
 * the first j artifacts of the sequence. The last node x of such an ordering is a maximal node of D,
 * so best(D, j) is the largest of best(D, j - 1), best(D - x, j) and, where x's data equals the j-th
 * artifact's, best(D - x, j - 1) + 1, over the maximal x of D. The down-sets are visited by size,
 * from the empty one up to the whole graph, each a step from a smaller one by a node all of whose
 * predecessors it holds; the choices are kept for the traceback.
 * <p>
 * A graph of one chain has as many down-sets as nodes, so this is the usual LCS table; each
 * branch point of two branches of lengths p and q adds about p * q; k branches open at once
 * multiply their lengths. Telling whether a sequence interleaves k chains is NP-complete in k, so
 * no exact alignment avoids that growth: past {@link #BUDGET_BYTES} of table this gives up
 * ({@code null}) and the caller aligns as before. The budget is a fixed estimate, not a share of the
 * heap: the alignment - and so what a repository stores - must not depend on the machine.
 * <p>
 * Leading and trailing artifacts where the graph has a single first (last) node with the same data
 * are matched up front, as alignPaths matches trailing ones: every ordering starts (ends) with
 * that node, so matching them is optimal - and an unchanged file needs no table.
 */
final class DownsetAlignment {

	/**
	 * The most memory the table may take, estimated: per down-set its choices (a byte per artifact),
	 * the step taken where it has several (an int per artifact), and while its layer and the next
	 * are being filled its row (an int per artifact) and node set. Tests run with Gradle's 512 MB.
	 */
	static final long BUDGET_BYTES = 64L << 20;

	/** A down-set's fixed cost: the object, its steps' lists. */
	private static final long STATE_BYTES = 128;

	private static final byte LEFT = 1, UP = 2, MATCH = 3;

	private DownsetAlignment() {
	}

	/**
	 * The nodes of [other] in order if it is a single chain from head to tail, else {@code null}.
	 */
	static PartialOrderGraph.Node.Op[] chain(PartialOrderGraph.Op other) {
		List<PartialOrderGraph.Node.Op> nodes = new ArrayList<>();
		PartialOrderGraph.Node.Op node = other.getHead();
		while (true) {
			Collection<? extends PartialOrderGraph.Node.Op> next = node.getNext();
			// the tail - for an empty sequence the head's edge to it, which may be there twice
			if (!next.isEmpty() && next.stream().allMatch(n -> n == other.getTail()))
				return nodes.toArray(new PartialOrderGraph.Node.Op[0]);
			if (next.size() != 1)
				return null;
			node = next.iterator().next();
			if (node.getPrevious().size() != 1)
				return null;
			nodes.add(node);
		}
	}

	/**
	 * Which node of [sequence] each node of [graph] is matched to, keyed by the graph node's sequence
	 * number (as alignPaths), or {@code null} if the table would exceed the budget or the graph's
	 * sequence numbers repeat (the keys would collide).
	 */
	static MutableIntObjectMap<PartialOrderGraph.Node.Op> align(PartialOrderGraph.Op graph, PartialOrderGraph.Node.Op[] sequence) {
		// the graph's nodes, numbered; head and tail left out
		List<PartialOrderGraph.Node.Op> nodes = new ArrayList<>();
		for (PartialOrderGraph.Node.Op node : graph.collectNodes())
			if (node != graph.getHead() && node != graph.getTail())
				nodes.add(node);
		int n = nodes.size();
		Map<PartialOrderGraph.Node.Op, Integer> index = new IdentityHashMap<>();
		for (int i = 0; i < n; i++)
			index.put(nodes.get(i), i);

		MutableIntSet sequenceNumbers = new IntHashSet();
		for (PartialOrderGraph.Node.Op node : nodes)
			if (data(node) != null && !sequenceNumbers.add(node.getSequenceNumber()))
				return null;

		int[][] previous = new int[n][];
		int[][] next = new int[n][];
		for (int i = 0; i < n; i++) {
			previous[i] = inner(nodes.get(i).getPrevious(), index);
			next[i] = inner(nodes.get(i).getNext(), index);
		}

		// data as ints, -1 matching nothing
		Map<Object, Integer> ids = new HashMap<>();
		int[] nodeData = new int[n];
		for (int i = 0; i < n; i++)
			nodeData[i] = id(data(nodes.get(i)), ids, true);
		int[] seqData = new int[sequence.length];
		for (int j = 0; j < sequence.length; j++)
			seqData[j] = id(data(sequence[j]), ids, false);

		MutableIntObjectMap<PartialOrderGraph.Node.Op> result = IntObjectMaps.mutable.empty();

		// leading: while the graph has one first node and it matches the next artifact
		BitSet start = new BitSet(n);
		int[] first = enabled(start, n, previous);
		int from = 0;
		while (from < sequence.length && first.length == 1 && nodeData[first[0]] >= 0 && nodeData[first[0]] == seqData[from]) {
			int x = first[0];
			result.put(nodes.get(x).getSequenceNumber(), sequence[from]);
			start.set(x);
			first = successorEnabled(first, x, start, next, previous);
			from++;
		}
		// trailing: while what is left has one last node and it matches the last artifact
		BitSet end = new BitSet(n);
		end.set(0, n);
		int remaining = n - start.cardinality();
		int[] last = maximal(n, start, next);
		int to = sequence.length;
		while (to > from && remaining > 0 && last.length == 1 && nodeData[last[0]] >= 0 && nodeData[last[0]] == seqData[to - 1]) {
			int x = last[0];
			result.put(nodes.get(x).getSequenceNumber(), sequence[to - 1]);
			end.clear(x);
			last = predecessorMaximal(last, x, end, start, next, previous);
			remaining--;
			to--;
		}
		int m = to - from;
		int layers = remaining;
		if (m == 0 || layers == 0)
			return result;

		// the table, layer by layer: layer k holds the down-sets with k more nodes than start
		State initial = new State(start, first);
		initial.row = new int[m + 1];
		initial.choice = new byte[m + 1];
		long rowBytes = 4L * (m + 1);
		long liveBytes = rowBytes + n / 8 + 16; // a layer's rows and node sets, freed when it is done
		long bytes = (m + 1) + STATE_BYTES + liveBytes;
		Map<BitSet, State> layer = new HashMap<>(Map.of(start, initial));
		for (int k = 0; k < layers; k++) {
			Map<BitSet, State> nextLayer = new HashMap<>();
			for (State state : layer.values()) {
				for (int x : state.enabled) {
					if (!end.get(x))
						continue;
					BitSet set = (BitSet) state.set.clone();
					set.set(x);
					State successor = nextLayer.get(set);
					if (successor == null) {
						successor = new State(set, successorEnabled(state.enabled, x, set, next, previous));
						nextLayer.put(set, successor);
						bytes += (m + 1) + STATE_BYTES + liveBytes;
						if (bytes > BUDGET_BYTES)
							return null;
					}
					successor.from.add(state);
					successor.via.add(x);
				}
			}
			for (State state : nextLayer.values())
				if (state.from.size() > 1)
					bytes += rowBytes; // its steps
			if (bytes > BUDGET_BYTES)
				return null;
			for (State state : nextLayer.values())
				state.fill(m, nodeData, seqData, from);
			bytes -= liveBytes * layer.size();
			for (State state : layer.values()) {
				state.row = null; // only the choices are needed from here on
				state.set = null;
				state.enabled = null;
			}
			layer = nextLayer;
		}

		// traceback from the whole graph (minus the trailing part) and the whole sequence
		State state = layer.get(end);
		int j = m;
		while (j > 0 && state.choice != null && state.choice[j] != 0) {
			byte c = state.choice[j];
			if (c == LEFT) {
				j--;
				continue;
			}
			int edge = state.edge == null ? 0 : state.edge[j];
			int x = state.via.get(edge);
			if (c == MATCH) {
				result.put(nodes.get(x).getSequenceNumber(), sequence[from + j - 1]);
				j--;
			}
			state = state.from.get(edge);
		}
		return result;
	}

	/** One down-set of the table: its incoming steps (from a smaller down-set by a node), its row and choices. */
	private static final class State {
		BitSet set;
		int[] enabled;
		final List<State> from = new ArrayList<>(1);
		final List<Integer> via = new ArrayList<>(1);
		int[] row;
		byte[] choice;
		/** Which incoming step each choice took - only where there are several. */
		int[] edge;

		State(BitSet set, int[] enabled) {
			this.set = set;
			this.enabled = enabled;
		}

		/** The row: a match first, then the longer of skipping the node and skipping the artifact, ties to the artifact - alignPaths' preference. */
		void fill(int m, int[] nodeData, int[] seqData, int offset) {
			this.row = new int[m + 1];
			this.choice = new byte[m + 1];
			if (this.from.size() > 1)
				this.edge = new int[m + 1];
			for (int j = 1; j <= m; j++) {
				int best = this.row[j - 1];
				byte c = LEFT;
				int e = 0;
				for (int s = 0; s < this.from.size(); s++) {
					int[] prev = this.from.get(s).row;
					int x = this.via.get(s);
					boolean equal = nodeData[x] >= 0 && nodeData[x] == seqData[offset + j - 1];
					if (equal && (prev[j - 1] + 1 > best || prev[j - 1] + 1 == best && c != MATCH)) {
						best = prev[j - 1] + 1;
						c = MATCH;
						e = s;
					} else if (prev[j] > best) {
						best = prev[j];
						c = UP;
						e = s;
					}
				}
				this.row[j] = best;
				this.choice[j] = c;
				if (this.edge != null)
					this.edge[j] = e;
			}
		}
	}

	private static int[] inner(Collection<? extends PartialOrderGraph.Node.Op> neighbours, Map<PartialOrderGraph.Node.Op, Integer> index) {
		return neighbours.stream().map(index::get).filter(Objects::nonNull).mapToInt(Integer::intValue).toArray();
	}

	/** The nodes not in [set] all of whose predecessors are. */
	private static int[] enabled(BitSet set, int n, int[][] previous) {
		List<Integer> enabled = new ArrayList<>();
		for (int i = 0; i < n; i++)
			if (!set.get(i) && Arrays.stream(previous[i]).allMatch(set::get))
				enabled.add(i);
		return enabled.stream().mapToInt(Integer::intValue).toArray();
	}

	/** The enabled nodes of [set] = the predecessor's [enabled] plus [x]: x goes, x's successors come in once ready. */
	private static int[] successorEnabled(int[] enabled, int x, BitSet set, int[][] next, int[][] previous) {
		int[] result = new int[enabled.length - 1 + next[x].length];
		int size = 0;
		for (int e : enabled)
			if (e != x)
				result[size++] = e;
		for (int y : next[x])
			if (Arrays.stream(previous[y]).allMatch(set::get))
				result[size++] = y;
		return Arrays.copyOf(result, size);
	}

	/** The nodes not in [start] none of whose successors is a node (the tail is none). */
	private static int[] maximal(int n, BitSet start, int[][] next) {
		List<Integer> maximal = new ArrayList<>();
		for (int i = 0; i < n; i++)
			if (!start.get(i) && next[i].length == 0)
				maximal.add(i);
		return maximal.stream().mapToInt(Integer::intValue).toArray();
	}

	/** The maximal nodes once [x] left [end]: [maximal] without x, with each predecessor of x outside [start] none of whose successors is left. */
	private static int[] predecessorMaximal(int[] maximal, int x, BitSet end, BitSet start, int[][] next, int[][] previous) {
		int[] result = new int[maximal.length - 1 + previous[x].length];
		int size = 0;
		for (int e : maximal)
			if (e != x)
				result[size++] = e;
		for (int p : previous[x])
			if (!start.get(p) && Arrays.stream(next[p]).noneMatch(end::get))
				result[size++] = p;
		return Arrays.copyOf(result, size);
	}

	private static Object data(PartialOrderGraph.Node.Op node) {
		Artifact<?> artifact = node.getArtifact();
		return artifact == null ? null : artifact.getData();
	}

	private static int id(Object data, Map<Object, Integer> ids, boolean add) {
		if (data == null)
			return -1;
		Integer id = ids.get(data);
		if (id == null) {
			if (!add)
				return -2; // in the sequence only: matches no node (nodes are -1 or >= 0)
			id = ids.size();
			ids.put(data, id);
		}
		return id;
	}
}
