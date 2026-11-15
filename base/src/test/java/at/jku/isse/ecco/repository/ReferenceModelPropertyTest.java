package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.test.util.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Commit and checkout against a reference model (NFR-R3): a variant is a set of files, each a list of
 * lines, and a history is a list of commits of variants under distinct configurations. jqwik generates
 * histories, commits them into a bare {@link Repository.Op} the way {@code CommitService} does
 * (extract, retroactive conditions, invalidated main tree), and checks the repository against the
 * model - no adapter, no persistence. A failing history is shrunk to a smallest one that still fails.
 * <p>
 * Every configuration is distinguishable from every other only if the repository's maximum order
 * reaches the number of features (a module of each configuration's full literal set exists); the
 * properties that rely on that generate histories with maximum order = number of features.
 */
class ReferenceModelPropertyTest {

	private static final List<String> FILES = List.of("F1", "F2", "F3");
	/** The lines' global order: a variant's lines are a subsequence of it unless a property permutes them. */
	private static final List<String> LINES = List.of("a", "b", "c", "d", "e", "f");

	// ---------------------------------------------------------------------------------------------
	// The model

	/** One commit: the features of its configuration (by index) and its files' lines. */
	record ModelCommit(SortedSet<Integer> features, SortedMap<String, List<String>> files) {
		@Override
		public String toString() {
			return features.stream().map(i -> "f" + i).collect(Collectors.joining(",", "{", "}")) + " -> " + files;
		}
	}

	/** A history: the number of features and the commits in order, their configurations distinct. */
	record History(int numFeatures, List<ModelCommit> commits) {
	}

	/** Every artifact of a variant as a path: a file ("F1") and each line of it ("F1/a"). */
	static Set<String> paths(SortedMap<String, List<String>> files) {
		Set<String> paths = new HashSet<>();
		files.forEach((file, lines) -> {
			paths.add(file);
			lines.forEach(line -> paths.add(file + "/" + line));
		});
		return paths;
	}

	// ---------------------------------------------------------------------------------------------
	// Generators

	@Provide
	Arbitrary<History> orderedHistories() {
		return histories(false);
	}

	@Provide
	Arbitrary<History> permutedHistories() {
		return histories(true);
	}

	private Arbitrary<History> histories(boolean permuteLines) {
		return Arbitraries.integers().between(1, 4).flatMap(numFeatures -> {
			Arbitrary<SortedSet<Integer>> configurations = Arbitraries.integers().between(0, numFeatures - 1)
					.set().ofMinSize(1).ofMaxSize(numFeatures)
					.map(TreeSet::new);
			Arbitrary<ModelCommit> commits = Combinators.combine(configurations, variants(permuteLines)).as(ModelCommit::new);
			return commits.list().ofMinSize(1).ofMaxSize(6)
					.uniqueElements(ModelCommit::features)
					.map(list -> new History(numFeatures, list));
		});
	}

	private Arbitrary<SortedMap<String, List<String>>> variants(boolean permuteLines) {
		Arbitrary<List<String>> lines = Arbitraries.of(LINES).set().ofMaxSize(LINES.size())
				.flatMap(set -> permuteLines
						? Arbitraries.shuffle(new ArrayList<>(set))
						: Arbitraries.just(LINES.stream().filter(set::contains).toList()));
		return Arbitraries.maps(Arbitraries.of(FILES), lines).ofMinSize(1).ofMaxSize(FILES.size()).map(TreeMap::new);
	}

	// ---------------------------------------------------------------------------------------------
	// The implementation, driven as CommitService and CheckoutService drive it

	private static final class Driver {
		final EntityFactory ef = new SerEntityFactory();
		final Repository.Op repository = ef.createRepository();

		Driver(int maxOrder) {
			repository.setMaxOrder(maxOrder);
		}

		/** The configuration of [features]: feature fi with revision 1, as the configuration parser makes it. */
		Configuration configuration(Set<Integer> features) {
			FeatureRevision[] revisions = features.stream().map(i -> {
				Feature feature = ef.createFeature("f" + i, "f" + i);
				return feature.addRevision("1");
			}).toArray(FeatureRevision[]::new);
			return ef.createConfiguration(revisions);
		}

		void commit(ModelCommit commit) {
			Set<Node.Op> nodes = new HashSet<>();
			commit.files().forEach((file, lines) -> {
				Node.Op fileNode = ef.createOrderedNode(new TestArtifactData(file));
				for (String line : lines)
					fileNode.addChild(ef.createNode(new TestArtifactData(line)));
				nodes.add(fileNode);
			});
			repository.extract(configuration(commit.features()), nodes, "property");
			repository.setRetroactiveConditions();
			repository.invalidateMainTree();
		}

		/** The checkout of [features] as the files a writer would write: each file node with its children in order. */
		Checkout checkout(Set<Integer> features) {
			return repository.compose(configuration(features));
		}
	}

	/** What a writer writes for a checkout, a file's name to its lines; a file written twice fails. */
	static SortedMap<String, List<String>> written(Checkout checkout) {
		SortedMap<String, List<String>> files = new TreeMap<>();
		for (Node fileNode : checkout.getNode().getChildren()) {
			String file = fileNode.getArtifact().getData().toString();
			List<String> lines = fileNode.getChildren().stream().map(n -> n.getArtifact().getData().toString()).toList();
			assertNull(files.put(file, lines), "file " + file + " written twice");
		}
		return files;
	}

	/** The artifacts an association holds: its unique nodes, as paths. */
	static List<String> uniquePaths(Association.Op association) {
		List<String> paths = new ArrayList<>();
		collectUniquePaths(association.getRootNode(), "", paths);
		return paths;
	}

	private static void collectUniquePaths(Node node, String prefix, List<String> paths) {
		String path = prefix;
		if (node.getArtifact() != null) {
			path = prefix.isEmpty() ? node.getArtifact().getData().toString() : prefix + "/" + node.getArtifact().getData();
			if (node.isUnique())
				paths.add(path);
		}
		for (Node child : node.getChildren())
			collectUniquePaths(child, path, paths);
	}

	// ---------------------------------------------------------------------------------------------
	// Properties

	/**
	 * NFR-R3 without persistence: after the whole history, the checkout of every committed
	 * configuration writes exactly the files and lines committed under it, in their order.
	 */
	@Property(tries = 500)
	void checkoutReproducesEveryCommittedVariant(@ForAll("orderedHistories") History history) {
		Driver driver = new Driver(history.numFeatures());
		history.commits().forEach(driver::commit);

		for (ModelCommit commit : history.commits()) {
			Checkout checkout = driver.checkout(commit.features());
			assertEquals(commit.files(), written(checkout), "checkout of " + commit.features());
			assertTrue(checkout.getSurplusModules().isEmpty(), "SURPLUS for a committed configuration " + commit.features());
		}
	}

	/**
	 * Lines in any order, so variants can disagree on it: the checkout of every committed
	 * configuration still writes exactly its files and lines; where the order differs from the
	 * committed one, the checkout warns ORDER (FR-K5c).
	 */
	@Property(tries = 500)
	void checkoutKeepsTheContentWhenVariantsDisagreeOnOrder(@ForAll("permutedHistories") History history) {
		Driver driver = new Driver(history.numFeatures());
		history.commits().forEach(driver::commit);

		for (ModelCommit commit : history.commits()) {
			Checkout checkout = driver.checkout(commit.features());
			SortedMap<String, List<String>> written = written(checkout);
			assertEquals(commit.files().keySet(), written.keySet(), "files of " + commit.features());
			for (String file : commit.files().keySet()) {
				List<String> expected = commit.files().get(file);
				List<String> actual = written.get(file);
				assertEquals(new HashSet<>(expected), new HashSet<>(actual), "lines of " + file + " in " + commit.features());
				assertEquals(expected.size(), actual.size(), "a line of " + file + " written twice in " + commit.features());
				if (!expected.equals(actual))
					assertFalse(checkout.getOrderWarnings().isEmpty(), file + " written as " + actual + " instead of " + expected + " without an ORDER warning");
			}
		}
	}

	/**
	 * After every commit the associations partition the artifacts committed so far: each line is held
	 * by exactly one association, and none is held that was never committed. With the lines in their
	 * global order no two variants disagree on it, so a line of a file is one artifact. Holds for any
	 * maximum order - it is about the trees, not the conditions.
	 */
	@Property(tries = 500)
	void associationsPartitionTheCommittedLines(@ForAll("orderedHistories") History history,
	                                            @ForAll @IntRange(min = 1, max = 4) int maxOrder) {
		Driver driver = new Driver(maxOrder);
		Set<String> committed = new HashSet<>();
		for (ModelCommit commit : history.commits()) {
			driver.commit(commit);
			committed.addAll(paths(commit.files()));

			List<String> held = driver.repository.getAssociations().stream()
					.flatMap(association -> uniquePaths(association).stream()).toList();
			Set<String> distinct = new HashSet<>(held);
			assertEquals(distinct.size(), held.size(), "a line held by two associations: " + duplicates(held));
			assertEquals(committed, distinct, "the associations hold other lines than were committed");
			assertNoEmptyAssociation(driver.repository);
		}
	}

	/**
	 * Where variants disagree on the order, alignment may keep one line as two artifacts at different
	 * positions (two sequence numbers); still each artifact is held by exactly one association, and
	 * the lines held are exactly the lines committed.
	 */
	@Property(tries = 500)
	void associationsPartitionTheCommittedArtifacts(@ForAll("permutedHistories") History history,
	                                                @ForAll @IntRange(min = 1, max = 4) int maxOrder) {
		Driver driver = new Driver(maxOrder);
		Set<String> committed = new HashSet<>();
		for (ModelCommit commit : history.commits()) {
			driver.commit(commit);
			committed.addAll(paths(commit.files()));

			Set<Artifact<?>> held = Collections.newSetFromMap(new IdentityHashMap<>());
			for (Association.Op association : driver.repository.getAssociations())
				for (Artifact<?> artifact : uniqueArtifacts(association))
					assertTrue(held.add(artifact), "artifact " + artifact + " held by two associations");
			Set<String> lines = driver.repository.getAssociations().stream()
					.flatMap(association -> uniquePaths(association).stream()).collect(Collectors.toSet());
			assertEquals(committed, lines, "the associations hold other lines than were committed");
			assertNoEmptyAssociation(driver.repository);
		}
	}

	private static void assertNoEmptyAssociation(Repository.Op repository) {
		assertTrue(repository.getAssociations().stream().noneMatch(a -> a.getRootNode().getChildren().isEmpty()),
				"an association without artifacts");
	}

	/** The artifacts of an association's unique nodes, by identity. */
	static List<Artifact<?>> uniqueArtifacts(Association.Op association) {
		List<Artifact<?>> artifacts = new ArrayList<>();
		Deque<Node> todo = new ArrayDeque<>(List.of(association.getRootNode()));
		while (!todo.isEmpty()) {
			Node node = todo.pop();
			if (node.isUnique() && node.getArtifact() != null)
				artifacts.add(node.getArtifact());
			todo.addAll(node.getChildren());
		}
		return artifacts;
	}

	private static List<String> duplicates(List<String> paths) {
		Set<String> seen = new HashSet<>();
		return paths.stream().filter(p -> !seen.add(p)).toList();
	}

	@Example
	void theModelsPathsAreFilesAndLines() {
		SortedMap<String, List<String>> files = new TreeMap<>(Map.of("F1", List.of("a", "b"), "F2", List.of()));
		assertEquals(Set.of("F1", "F1/a", "F1/b", "F2"), paths(files));
	}
}
