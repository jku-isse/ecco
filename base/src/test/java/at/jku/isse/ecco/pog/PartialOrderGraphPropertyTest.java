package at.jku.isse.ecco.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.test.util.TestArtifactData;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partial order graphs against their definition. A graph is built by merging sequences that are all
 * subsequences of one global order, so no two of them disagree: every sequence stays a linearization
 * of the graph, and so does the global order restricted to the graph's artifacts.
 * <p>
 * Found here (gap #94): head -> {b, a -> c} -> tail (merge [b], then [a, c]) has the orderings
 * b,a,c, a,b,c and a,c,b, but {@link PartialOrderGraph.Op#collectNodeSequencings()} lists only b,a,c
 * and a,c,b - it never interleaves two branches - and the alignment over them matched two of
 * [a, b, c]: the merge kept b as a second node. A sequence is now aligned by {@link DownsetAlignment};
 * the sequencings themselves are still incomplete (gap #95), so that property stays disabled.
 */
class PartialOrderGraphPropertyTest {

	private static final List<String> ORDER = List.of("a", "b", "c", "d", "e");

	private final SerEntityFactory ef = new SerEntityFactory();

	@Provide
	Arbitrary<List<List<String>>> consistentSequences() {
		Arbitrary<List<String>> subsequence = Arbitraries.of(ORDER).set().ofMinSize(1)
				.map(set -> ORDER.stream().filter(set::contains).toList());
		return subsequence.list().ofMinSize(1).ofMaxSize(4);
	}

	/** Sequences in any order, so they can disagree and the graph holds one artifact as several nodes. */
	@Provide
	Arbitrary<List<List<String>>> anySequences() {
		Arbitrary<List<String>> sequence = Arbitraries.of(ORDER).list().ofMinSize(1).ofMaxSize(5);
		return sequence.list().ofMinSize(1).ofMaxSize(3);
	}

	private SerPartialOrderGraph graphOf(List<List<String>> sequences) {
		SerPartialOrderGraph graph = new SerPartialOrderGraph();
		for (List<String> sequence : sequences)
			graph.merge(artifacts(sequence));
		return graph;
	}

	private List<Artifact.Op<?>> artifacts(List<String> sequence) {
		List<Artifact.Op<?>> artifacts = new ArrayList<>();
		for (String data : sequence)
			artifacts.add(ef.createArtifact(new TestArtifactData(data)));
		return artifacts;
	}

	/** The graph's nodes other than head and tail. */
	private static List<PartialOrderGraph.Node.Op> inner(PartialOrderGraph.Op graph) {
		return graph.collectNodes().stream().filter(n -> n.getArtifact() != null).toList();
	}

	/** Every ordering of [nodes] in which each node comes after all nodes it can be reached from - by brute force. */
	private static Set<List<PartialOrderGraph.Node.Op>> linearExtensions(List<PartialOrderGraph.Node.Op> nodes) {
		Set<List<PartialOrderGraph.Node.Op>> result = new HashSet<>();
		extend(new ArrayList<>(), new ArrayList<>(nodes), result);
		return result;
	}

	private static void extend(List<PartialOrderGraph.Node.Op> prefix, List<PartialOrderGraph.Node.Op> rest, Set<List<PartialOrderGraph.Node.Op>> result) {
		if (rest.isEmpty()) {
			result.add(List.copyOf(prefix));
			return;
		}
		for (PartialOrderGraph.Node.Op candidate : List.copyOf(rest)) {
			boolean ready = candidate.getPrevious().stream().allMatch(p -> p.getArtifact() == null || prefix.contains(p));
			if (ready) {
				prefix.add(candidate);
				rest.remove(candidate);
				extend(prefix, rest, result);
				rest.add(candidate);
				prefix.remove(prefix.size() - 1);
			}
		}
	}

	private static String show(Collection<List<PartialOrderGraph.Node.Op>> orderings) {
		return orderings.stream()
				.map(o -> o.stream().map(n -> n.getArtifact().getData().toString()).collect(Collectors.joining(",")))
				.sorted().collect(Collectors.joining(" | "));
	}

	/**
	 * The alignment of a sequence matches as many artifacts as the best ordering of the graph allows
	 * (the longest common subsequence of the sequence and any ordering, by brute force), and only
	 * equal artifacts, each node and each artifact at most once, in an order the graph allows.
	 */
	@Property(tries = 1000)
	void alignmentIsOptimal(@ForAll("anySequences") List<List<String>> sequences,
	                        @ForAll("sequence") List<String> sequence) {
		SerPartialOrderGraph graph = graphOf(sequences);
		PartialOrderGraph.Node.Op[] chain = DownsetAlignment.chain(graph.fromList(artifacts(sequence)));
		assertNotNull(chain);

		var alignment = DownsetAlignment.align(graph, chain);
		assertNotNull(alignment, "within the budget");

		List<PartialOrderGraph.Node.Op> nodes = inner(graph);
		int best = linearExtensions(nodes).stream()
				.mapToInt(o -> lcs(o.stream().map(n -> n.getArtifact().getData().toString()).toList(), sequence))
				.max().orElse(0);
		assertEquals(best, alignment.size(), "matches of " + sequence + " in the graph of " + sequences);

		// the matched pairs: equal data, the artifacts distinct, and no artifact matched before one whose node the graph puts before it
		Map<Integer, PartialOrderGraph.Node.Op> bySequenceNumber = new HashMap<>();
		nodes.forEach(n -> bySequenceNumber.put(n.getSequenceNumber(), n));
		List<Integer> positions = new ArrayList<>();
		alignment.forEachKeyValue((sequenceNumber, artifactNode) -> {
			PartialOrderGraph.Node.Op node = bySequenceNumber.get(sequenceNumber);
			assertEquals(node.getArtifact().getData(), artifactNode.getArtifact().getData());
			positions.add(Arrays.asList(chain).indexOf(artifactNode));
		});
		assertEquals(positions.size(), new HashSet<>(positions).size(), "an artifact matched twice");
		alignment.forEachKeyValue((s1, a1) -> alignment.forEachKeyValue((s2, a2) -> {
			if (reaches(bySequenceNumber.get(s1), bySequenceNumber.get(s2)))
				assertTrue(Arrays.asList(chain).indexOf(a1) < Arrays.asList(chain).indexOf(a2), "matched against the graph's order");
		}));
	}

	@Provide
	Arbitrary<List<String>> sequence() {
		return Arbitraries.of(ORDER).list().ofMaxSize(6);
	}

	private static boolean reaches(PartialOrderGraph.Node.Op from, PartialOrderGraph.Node.Op to) {
		if (from == to)
			return false;
		Deque<PartialOrderGraph.Node.Op> todo = new ArrayDeque<>(from.getNext());
		Set<PartialOrderGraph.Node.Op> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		while (!todo.isEmpty()) {
			PartialOrderGraph.Node.Op node = todo.pop();
			if (node == to)
				return true;
			if (seen.add(node))
				todo.addAll(node.getNext());
		}
		return false;
	}

	private static int lcs(List<String> a, List<String> b) {
		int[][] t = new int[a.size() + 1][b.size() + 1];
		for (int i = 1; i <= a.size(); i++)
			for (int j = 1; j <= b.size(); j++)
				t[i][j] = a.get(i - 1).equals(b.get(j - 1)) ? t[i - 1][j - 1] + 1 : Math.max(t[i - 1][j], t[i][j - 1]);
		return t[a.size()][b.size()];
	}

	/** collectNodeSequencings() is what its javadoc and ConcurrentBranchSequencingTest say: every topological ordering. */
	@Property(tries = 300)
	@Disabled("gap #95: collectNodeSequencings() does not interleave branches; aligning two graphs still relies on it")
	void sequencingsAreAllLinearExtensions(@ForAll("consistentSequences") List<List<String>> sequences) {
		SerPartialOrderGraph graph = graphOf(sequences);

		Set<List<PartialOrderGraph.Node.Op>> sequencings = Arrays.stream(graph.collectNodeSequencings())
				.map(List::of).collect(Collectors.toSet());
		Set<List<PartialOrderGraph.Node.Op>> expected = linearExtensions(inner(graph));

		assertEquals(show(expected), show(sequencings), "graph of " + sequences);
	}

	/**
	 * Merging a sequence the graph already allows adds no node: the alignment matches every artifact
	 * of it. A node added here is a second artifact for the same line in the repository.
	 */
	@Property(tries = 300)
	void mergingAnAllowedOrderAddsNoNode(@ForAll("consistentSequences") List<List<String>> sequences) {
		SerPartialOrderGraph graph = graphOf(sequences);
		Set<String> present = sequences.stream().flatMap(List::stream).collect(Collectors.toSet());
		List<String> allowed = ORDER.stream().filter(present::contains).toList();
		int before = inner(graph).size();

		graph.merge(artifacts(allowed));

		assertEquals(before, inner(graph).size(), "merging " + allowed + " into the graph of " + sequences);
	}

	private static boolean isSubsequence(List<String> sequence, List<String> of) {
		int i = 0;
		for (String data : of)
			if (i < sequence.size() && sequence.get(i).equals(data))
				i++;
		return i == sequence.size();
	}

	/** Each merged sequence is still an ordering the graph allows (a subsequence of one of its linearizations). */
	@Property(tries = 300)
	void everyMergedSequenceRemainsALinearization(@ForAll("consistentSequences") List<List<String>> sequences) {
		SerPartialOrderGraph graph = graphOf(sequences);
		Set<List<String>> extensions = linearExtensions(inner(graph)).stream()
				.map(o -> o.stream().map(n -> n.getArtifact().getData().toString()).toList())
				.collect(Collectors.toSet());
		for (List<String> sequence : sequences)
			assertTrue(extensions.stream().anyMatch(e -> isSubsequence(sequence, e)),
					sequence + " is no longer an ordering of the graph of " + sequences);
	}
}
