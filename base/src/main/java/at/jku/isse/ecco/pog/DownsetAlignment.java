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
		/** For {@link #alignGraphs}: which step into the other graph's down-set each choice took. */
		short[] otherStep;

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

	/** The most nodes an alignment of [graph] and [other] can match: per datum, the fewer of the two graphs' nodes holding it. */
	static int matchBound(PartialOrderGraph.Op graph, PartialOrderGraph.Op other) {
		Map<Object, Integer> counts = new HashMap<>();
		for (PartialOrderGraph.Node.Op node : graph.collectNodes())
			if (data(node) != null)
				counts.merge(data(node), 1, Integer::sum);
		Map<Object, Integer> otherCounts = new HashMap<>();
		for (PartialOrderGraph.Node.Op node : other.collectNodes())
			if (data(node) != null)
				otherCounts.merge(data(node), 1, Integer::sum);
		int bound = 0;
		for (Map.Entry<Object, Integer> entry : otherCounts.entrySet())
			bound += Math.min(entry.getValue(), counts.getOrDefault(entry.getKey(), 0));
		return bound;
	}

	/** A graph's nodes, numbered (head and tail left out), with their predecessors, successors and data ids. */
	private static final class Side {
		final List<PartialOrderGraph.Node.Op> nodes = new ArrayList<>();
		final int[][] previous;
		final int[][] next;
		final int[] data;
		final int n;

		Side(PartialOrderGraph.Op graph, Map<Object, Integer> ids) {
			for (PartialOrderGraph.Node.Op node : graph.collectNodes())
				if (node != graph.getHead() && node != graph.getTail())
					this.nodes.add(node);
			this.n = this.nodes.size();
			Map<PartialOrderGraph.Node.Op, Integer> index = new IdentityHashMap<>();
			for (int i = 0; i < this.n; i++)
				index.put(this.nodes.get(i), i);
			this.previous = new int[this.n][];
			this.next = new int[this.n][];
			this.data = new int[this.n];
			for (int i = 0; i < this.n; i++) {
				this.previous[i] = inner(this.nodes.get(i).getPrevious(), index);
				this.next[i] = inner(this.nodes.get(i).getNext(), index);
				this.data[i] = id(data(this.nodes.get(i)), ids, true);
			}
		}
	}

	/** A down-set of the other graph: the steps it is reached by - a smaller down-set and the node added - and its enabled nodes. */
	private static final class OtherState {
		final BitSet set;
		int[] enabled;
		final List<Integer> from = new ArrayList<>(1);
		final List<Integer> via = new ArrayList<>(1);

		OtherState(BitSet set, int[] enabled) {
			this.set = set;
			this.enabled = enabled;
		}
	}

	/**
	 * Which node of [other] each node of [graph] is matched to, keyed by the graph node's sequence
	 * number, as {@link #align} does for a sequence: the most matches over every ordering both graphs
	 * allow - gap #95, where aligning two graphs used the orderings collectNodeSequencings lists, which
	 * never interleave two branches. The same table, its rows indexed by the other graph's down-sets
	 * instead of a sequence's prefixes: best(D, E) is the largest of best(D - x, E), best(D, E - y) and,
	 * where x's data equals y's, best(D - x, E - y) + 1, over the maximal x of D and y of E. A match
	 * so found is an ordering of both graphs at once, so the merged graph has no cycle. Null if the
	 * table would exceed the budget or the graph's sequence numbers repeat.
	 */
	static MutableIntObjectMap<PartialOrderGraph.Node.Op> alignGraphs(PartialOrderGraph.Op graph, PartialOrderGraph.Op other) {
		Map<Object, Integer> ids = new HashMap<>();
		Side a = new Side(graph, ids);
		Side b = new Side(other, ids);
		MutableIntSet sequenceNumbers = new IntHashSet();
		for (PartialOrderGraph.Node.Op node : a.nodes)
			if (data(node) != null && !sequenceNumbers.add(node.getSequenceNumber()))
				return null;
		MutableIntObjectMap<PartialOrderGraph.Node.Op> result = IntObjectMaps.mutable.empty();

		// leading: while both have one first node and their data are the same
		BitSet startA = new BitSet(a.n), startB = new BitSet(b.n);
		int[] firstA = enabled(startA, a.n, a.previous), firstB = enabled(startB, b.n, b.previous);
		while (firstA.length == 1 && firstB.length == 1 && a.data[firstA[0]] >= 0 && a.data[firstA[0]] == b.data[firstB[0]]) {
			int x = firstA[0], y = firstB[0];
			result.put(a.nodes.get(x).getSequenceNumber(), b.nodes.get(y));
			startA.set(x);
			startB.set(y);
			firstA = successorEnabled(firstA, x, startA, a.next, a.previous);
			firstB = successorEnabled(firstB, y, startB, b.next, b.previous);
		}
		// trailing: while what is left of both has one last node and their data are the same
		BitSet endA = new BitSet(a.n), endB = new BitSet(b.n);
		endA.set(0, a.n);
		endB.set(0, b.n);
		int remainingA = a.n - startA.cardinality(), remainingB = b.n - startB.cardinality();
		int[] lastA = maximal(a.n, startA, a.next), lastB = maximal(b.n, startB, b.next);
		while (remainingA > 0 && remainingB > 0 && lastA.length == 1 && lastB.length == 1
				&& a.data[lastA[0]] >= 0 && a.data[lastA[0]] == b.data[lastB[0]]) {
			int x = lastA[0], y = lastB[0];
			result.put(a.nodes.get(x).getSequenceNumber(), b.nodes.get(y));
			endA.clear(x);
			endB.clear(y);
			lastA = predecessorMaximal(lastA, x, endA, startA, a.next, a.previous);
			lastB = predecessorMaximal(lastB, y, endB, startB, b.next, b.previous);
			remainingA--;
			remainingB--;
		}
		if (remainingA == 0 || remainingB == 0)
			return result;

		// the other graph's down-sets between its start and its end, in layers: an index is a column
		List<OtherState> columns = new ArrayList<>();
		long bytes = 0;
		{
			OtherState initial = new OtherState(startB, firstB);
			columns.add(initial);
			Map<BitSet, Integer> layer = new HashMap<>(Map.of(startB, 0));
			for (int k = 0; k < remainingB; k++) {
				Map<BitSet, Integer> nextLayer = new HashMap<>();
				for (int s : layer.values()) {
					OtherState state = columns.get(s);
					for (int y : state.enabled) {
						if (!endB.get(y))
							continue;
						BitSet set = (BitSet) state.set.clone();
						set.set(y);
						Integer t = nextLayer.get(set);
						if (t == null) {
							t = columns.size();
							columns.add(new OtherState(set, successorEnabled(state.enabled, y, set, b.next, b.previous)));
							nextLayer.put(set, t);
							bytes += STATE_BYTES + b.n / 8 + 16;
							if (bytes > BUDGET_BYTES)
								return null;
						}
						columns.get(t).from.add(s);
						columns.get(t).via.add(y);
						if (columns.get(t).from.size() > Short.MAX_VALUE)
							return null; // the step is kept as a short
					}
				}
				for (int s : layer.values())
					columns.get(s).enabled = null;
				layer = nextLayer;
			}
		}
		int m = columns.size();
		int endColumn = columns.size() - 1; // the last layer has the one down-set holding all of [startB, endB]

		// this graph's down-sets, layer by layer, each with a row over the columns
		State initial = new State(startA, firstA);
		initial.row = new int[m];
		initial.choice = new byte[m];
		long rowBytes = 4L * m;
		long liveBytes = rowBytes + a.n / 8 + 16;
		bytes += 2L * m + STATE_BYTES + liveBytes;
		Map<BitSet, State> layer = new HashMap<>(Map.of(startA, initial));
		for (int k = 0; k < remainingA; k++) {
			Map<BitSet, State> nextLayer = new HashMap<>();
			for (State state : layer.values()) {
				for (int x : state.enabled) {
					if (!endA.get(x))
						continue;
					BitSet set = (BitSet) state.set.clone();
					set.set(x);
					State successor = nextLayer.get(set);
					if (successor == null) {
						successor = new State(set, successorEnabled(state.enabled, x, set, a.next, a.previous));
						nextLayer.put(set, successor);
						// choices and the step taken in the other graph (a short per column), the row while live
						bytes += 3L * m + STATE_BYTES + liveBytes;
						if (bytes > BUDGET_BYTES)
							return null;
					}
					successor.from.add(state);
					successor.via.add(x);
				}
			}
			for (State state : nextLayer.values())
				if (state.from.size() > 1)
					bytes += rowBytes;
			if (bytes > BUDGET_BYTES)
				return null;
			for (State state : nextLayer.values())
				fillGraphRow(state, columns, a.data, b.data);
			bytes -= liveBytes * layer.size();
			for (State state : layer.values()) {
				state.row = null;
				state.set = null;
				state.enabled = null;
			}
			layer = nextLayer;
		}

		// traceback from both whole graphs (minus what was matched up front)
		State state = layer.get(endA);
		int column = endColumn;
		while (column > 0 && state.choice != null && state.choice[column] != 0) {
			byte c = state.choice[column];
			if (c == LEFT) {
				int step = state.otherStep[column];
				column = columns.get(column).from.get(step);
				continue;
			}
			int edge = state.edge == null ? 0 : state.edge[column];
			if (c == MATCH) {
				int step = state.otherStep[column];
				OtherState o = columns.get(column);
				result.put(a.nodes.get(state.via.get(edge)).getSequenceNumber(), b.nodes.get(o.via.get(step)));
				column = o.from.get(step);
			}
			state = state.from.get(edge);
		}
		return result;
	}

	/** A row of {@link #alignGraphs}: a match first, then skipping this graph's node, then skipping the other's. */
	private static void fillGraphRow(State state, List<OtherState> columns, int[] dataA, int[] dataB) {
		int m = columns.size();
		state.row = new int[m];
		state.choice = new byte[m];
		state.otherStep = new short[m];
		if (state.from.size() > 1)
			state.edge = new int[m];
		for (int e = 1; e < m; e++) {
			OtherState o = columns.get(e);
			int best = -1;
			byte c = 0;
			int edge = 0, step = 0;
			for (int s = 0; s < state.from.size(); s++) {
				int[] prev = state.from.get(s).row;
				int x = state.via.get(s);
				for (int p = 0; p < o.from.size(); p++) {
					int y = o.via.get(p);
					if (dataA[x] >= 0 && dataA[x] == dataB[y] && prev[o.from.get(p)] + 1 > best) {
						best = prev[o.from.get(p)] + 1;
						c = MATCH;
						edge = s;
						step = p;
					}
				}
			}
			for (int s = 0; s < state.from.size(); s++) {
				int[] prev = state.from.get(s).row;
				if (prev[e] > best) {
					best = prev[e];
					c = UP;
					edge = s;
				}
			}
			for (int p = 0; p < o.from.size(); p++) {
				if (state.row[o.from.get(p)] > best) {
					best = state.row[o.from.get(p)];
					c = LEFT;
					step = p;
				}
			}
			state.row[e] = best;
			state.choice[e] = c;
			state.otherStep[e] = (short) step;
			if (state.edge != null)
				state.edge[e] = edge;
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
