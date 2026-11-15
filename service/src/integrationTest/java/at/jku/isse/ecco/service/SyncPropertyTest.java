package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.repository.Repository;
import net.jqwik.api.*;
import net.jqwik.api.statistics.Statistics;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence and sync against a model (NFR-R3, FR-D2c, FR-D3b/c): an origin with one to three
 * commits, a fork of it, then random commits, pulls and pushes on either side. The model is which
 * commits each repository holds, in order: a fork holds the origin's, a pull or push appends the
 * commits the receiving side lacks in the sending side's order. A fork, pull or push may leave a
 * feature out - every revision of it the sending side has - and then leaves out every commit with it
 * (FR-D4a, FR-D4c), which a later one without the exclusion still brings. Every step opens the
 * repository from disk and closes it, so each one is also a reopen. A failing history is shrunk to a
 * smallest one.
 * <p>
 * Two properties: with every configuration committed once and the maximum order at the number of
 * features (4), each repository holds exactly the model's commits and checks every one of them out
 * as committed - the model alone says what a checkout is. With configurations committed again with
 * other lines in any order, at the default maximum order (2), each repository checks out as a new one with the
 * model's commits committed in its order - FR-D3b's own statement. A checkout names each commit's
 * configuration with its revisions' ids, as the repository holds it: a feature made after the fork on
 * both sides is one feature with two revisions (FR-D3f), which a configuration by name cannot tell apart.
 */
class SyncPropertyTest {

	private static final List<String> FEATURES = List.of("A", "B", "C", "D");
	private static final List<String> FILES = List.of("a.txt", "b.txt");
	/** the lines' global order: every file's lines are a subsequence of it, unless they are permuted */
	private static final List<String> LINES = List.of("l1", "l2", "l3", "l4", "l5", "l6");

	// ---------------------------------------------------------------------------------------------
	// The model

	enum Side {
		ORIGIN, FORK;

		Side other() {
			return this == ORIGIN ? FORK : ORIGIN;
		}
	}

	enum Kind {COMMIT, PULL, PUSH}

	/** files to their lines */
	record Variant(SortedMap<String, List<String>> files) {
		@Override
		public String toString() {
			return files.toString();
		}
	}

	/**
	 * A commit of [side], or [side] pulling from or pushing to the other side - leaving out, where
	 * [excluded] names a feature, every revision of it the sending side has (FR-D4a).
	 */
	record Action(Kind kind, Side side, Variant variant, String excluded) {
		Action(Kind kind, Side side, Variant variant) {
			this(kind, side, variant, null);
		}

		@Override
		public String toString() {
			return kind == Kind.COMMIT ? side + " commits " + variant
					: side + " " + kind.name().toLowerCase() + (excluded == null ? "" : " without " + excluded);
		}
	}

	/**
	 * The commits before the fork, the actions after it, the configurations the commits take in turn,
	 * and the feature the fork leaves out, if any.
	 */
	record History(List<Variant> base, List<Action> actions, List<String> configurations, String forkExcluded) {
		History(List<Variant> base, List<Action> actions, List<String> configurations) {
			this(base, actions, configurations, null);
		}

		@Override
		public String toString() {
			StringBuilder text = new StringBuilder();
			int c = 0;
			for (Variant variant : base)
				text.append("\n  ORIGIN commits [").append(configurations.get(c++)).append("] ").append(variant);
			text.append("\n  FORK forks").append(forkExcluded == null ? "" : " without " + forkExcluded);
			for (Action action : actions) {
				if (action.kind() == Kind.COMMIT)
					text.append("\n  ").append(action.side()).append(" commits [").append(configurations.get(c++)).append("] ").append(action.variant());
				else
					text.append("\n  ").append(action);
			}
			return text.toString();
		}
	}

	record ModelCommit(String message, String configuration, Variant variant) {
		boolean has(String feature) {
			return List.of(configuration.split(", ")).contains(feature);
		}
	}

	/** commits a sync left out for an exclusion, counted by {@link #append} - for {@link #collectSynced} */
	private static final ThreadLocal<int[]> LEFT_OUT = ThreadLocal.withInitial(() -> new int[1]);

	/** what each side holds after the history, in order */
	static Map<Side, List<ModelCommit>> model(History history) {
		LEFT_OUT.get()[0] = 0;
		Map<Side, List<ModelCommit>> held = new EnumMap<>(Side.class);
		held.put(Side.ORIGIN, new ArrayList<>());
		int c = 0;
		for (Variant variant : history.base()) {
			held.get(Side.ORIGIN).add(new ModelCommit("c" + c, history.configurations().get(c), variant));
			c++;
		}
		held.put(Side.FORK, new ArrayList<>());
		append(held.get(Side.FORK), held.get(Side.ORIGIN), history.forkExcluded());
		for (Action action : history.actions()) {
			switch (action.kind()) {
				case COMMIT -> {
					held.get(action.side()).add(new ModelCommit("c" + c, history.configurations().get(c), action.variant()));
					c++;
				}
				case PULL -> append(held.get(action.side()), held.get(action.side().other()), action.excluded());
				case PUSH -> append(held.get(action.side().other()), held.get(action.side()), action.excluded());
			}
		}
		return held;
	}

	/** Whether a sync from [sender] leaves [feature] out: only a feature the sender has can be named (else it is refused). */
	static boolean excludes(List<ModelCommit> sender, String feature) {
		return feature != null && sender.stream().anyMatch(commit -> commit.has(feature));
	}

	/** How many commits a history's pulls and pushes move - so that a run shows it synced something. */
	static void collectSynced(History history, Map<Side, List<ModelCommit>> model) {
		int made = history.base().size() + (int) history.actions().stream().filter(a -> a.kind() == Kind.COMMIT).count();
		int held = model.get(Side.ORIGIN).size() + model.get(Side.FORK).size();
		int moved = held - made - history.base().size(); // the base is held twice by the fork alone
		Statistics.label("commits moved by pull and push").collect(moved == 0 ? "0" : moved <= 2 ? "1-2" : "3+");
		int leftOut = LEFT_OUT.get()[0];
		Statistics.label("commits left out by an exclusion").collect(leftOut == 0 ? "0" : leftOut <= 2 ? "1-2" : "3+");
	}

	/**
	 * FR-D3b/c: the commits of [from] that [into] lacks, after its own, in their order - FR-D4a: but
	 * those with a revision of [excluded]
	 */
	private static void append(List<ModelCommit> into, List<ModelCommit> from, String excluded) {
		boolean excluding = excludes(from, excluded);
		Set<String> had = into.stream().map(ModelCommit::message).collect(Collectors.toSet());
		for (ModelCommit commit : List.copyOf(from))
			if (!had.contains(commit.message())) {
				if (excluding && commit.has(excluded))
					LEFT_OUT.get()[0]++;
				else
					into.add(commit);
			}
	}

	// ---------------------------------------------------------------------------------------------
	// Generators

	/** variants whose lines are in the global order or, [permuted], in any order */
	private Arbitrary<Variant> variants(boolean permuted) {
		Arbitrary<List<String>> lines = Arbitraries.of(LINES).set().ofMinSize(1)
				.flatMap(set -> permuted ? Arbitraries.shuffle(new ArrayList<>(set)) : Arbitraries.just(LINES.stream().filter(set::contains).toList()));
		return Arbitraries.maps(Arbitraries.of(FILES), lines).ofMinSize(1).ofMaxSize(FILES.size())
				.map(files -> new Variant(new TreeMap<>(files)));
	}

	private Arbitrary<Action> actions(boolean permuted) {
		Arbitrary<Side> sides = Arbitraries.of(Side.class);
		return Arbitraries.frequencyOf(
				Tuple.of(3, Combinators.combine(sides, variants(permuted)).as((side, variant) -> new Action(Kind.COMMIT, side, variant))),
				Tuple.of(1, Combinators.combine(sides, exclusions()).as((side, excluded) -> new Action(Kind.PULL, side, null, excluded))),
				Tuple.of(1, Combinators.combine(sides, exclusions()).as((side, excluded) -> new Action(Kind.PUSH, side, null, excluded))));
	}

	/** no feature left out in half the syncs, else one of A-D */
	private Arbitrary<String> exclusions() {
		return Arbitraries.frequencyOf(Tuple.of(1, Arbitraries.just("")), Tuple.of(1, Arbitraries.of(FEATURES)))
				.map(feature -> feature.isEmpty() ? null : feature);
	}

	/** every non-empty set of features, as a configuration */
	private static List<String> allConfigurations(List<String> features) {
		List<String> all = new ArrayList<>();
		for (int mask = 1; mask < 1 << features.size(); mask++) {
			List<String> configuration = new ArrayList<>();
			for (int f = 0; f < features.size(); f++)
				if ((mask & 1 << f) != 0)
					configuration.add(features.get(f));
			all.add(String.join(", ", configuration));
		}
		return all;
	}

	/**
	 * The actions after the fork - in half the histories ending with the fork pulling and pushing,
	 * as collaborators do, so that most histories move commits both ways.
	 */
	private Arbitrary<List<Action>> afterFork(boolean permuted) {
		return Combinators.combine(actions(permuted).list().ofMinSize(1).ofMaxSize(8), Arbitraries.of(true, false)).as((actions, sync) -> {
			if (!sync)
				return actions;
			List<Action> synced = new ArrayList<>(actions);
			synced.add(new Action(Kind.PULL, Side.FORK, null));
			synced.add(new Action(Kind.PUSH, Side.FORK, null));
			return synced;
		});
	}

	/** each configuration committed once: at most 3 + 8 commits of the 15 configurations of A-D; the fork may leave a feature out */
	@Provide
	Arbitrary<History> onceEach() {
		return Combinators.combine(variants(false).list().ofMinSize(1).ofMaxSize(3), afterFork(false),
				Arbitraries.shuffle(allConfigurations(FEATURES)), exclusions()).as(History::new);
	}

	/**
	 * As {@link #onceEach}, the fork leaving nothing out: a fork leaving revisions out of an origin
	 * with a merge commit merges (FR-D3e), and the merge commits that leaves in the fork the origin
	 * lacks make later pushes merge - gap #96's rest
	 */
	@Provide
	Arbitrary<History> onceEachForkingAll() {
		return onceEach().map(h -> new History(h.base(), h.actions(), h.configurations(), null));
	}

	/**
	 * As {@link #onceEach}, without a feature both sides make after the fork: a merge refuses two
	 * features of one name (gap #87, FR-F6), a replay takes them as one (FR-D3f).
	 */
	@Provide
	Arbitrary<History> onceEachNoFeatureNewOnBothSides() {
		return onceEach().filter(SyncPropertyTest::noFeatureNewOnBothSides);
	}

	static boolean noFeatureNewOnBothSides(History history) {
		int c = 0;
		Set<String> base = new HashSet<>();
		for (int i = 0; i < history.base().size(); i++)
			base.addAll(List.of(history.configurations().get(c++).split(", ")));
		Map<Side, Set<String>> made = new EnumMap<>(Map.of(Side.ORIGIN, new HashSet<>(), Side.FORK, new HashSet<>()));
		for (Action action : history.actions())
			if (action.kind() == Kind.COMMIT)
				for (String feature : history.configurations().get(c++).split(", "))
					if (!base.contains(feature))
						made.get(action.side()).add(feature);
		return Collections.disjoint(made.get(Side.ORIGIN), made.get(Side.FORK));
	}

	/**
	 * Configurations of A-C, committed again with other lines, in any order: where variants disagree
	 * on the order, a checkout's order depends on the order of the commits, so a replay in another
	 * order than FR-D3b's shows.
	 */
	@Provide
	Arbitrary<History> withRepeats() {
		return Combinators.combine(variants(true).list().ofMinSize(1).ofMaxSize(3), afterFork(true),
				Arbitraries.of(allConfigurations(FEATURES.subList(0, 3))).list().ofSize(11), exclusions()).as(History::new);
	}

	// ---------------------------------------------------------------------------------------------
	// The implementation, every step a service of its own on the repository on disk

	/** No merge commit; one both sides have (made before the fork); one only the origin has (after it). */
	enum MergeCommit {NONE, BEFORE_FORK, AFTER_FORK}

	/** a commit without a configuration, as a merge of an older version recorded one per association */
	static void addMergeCommit(Path repository) {
		try (EccoService service = SyncHistory.open(repository)) {
			Repository.Op repo = (Repository.Op) service.getRepository();
			repo.addCommit(repo.getEntityFactory().createCommit(Repository.MERGE));
			service.store();
		}
	}

	private static final class Run implements AutoCloseable {
		final Path tmp;
		final Map<Side, Path> repositories = new EnumMap<>(Side.class);
		/** where the origin has a merge commit, as a fork, pull or push of an older version recorded */
		MergeCommit mergeCommit = MergeCommit.NONE;
		/** whether the fork, pulls and pushes go to a sync server serving the other side, over a loopback socket */
		boolean network;

		Run() throws IOException {
			this.tmp = Files.createTempDirectory("sync-property");
		}

		/** the history on the repositories, [maxOrder] set on the origin before its first commit (null: the default) */
		void play(History history, Integer maxOrder) throws Exception {
			Path origin = init(tmp.resolve("origin"), maxOrder);
			if (mergeCommit == MergeCommit.BEFORE_FORK)
				addMergeCommit(origin);
			repositories.put(Side.ORIGIN, origin);
			int c = 0;
			for (Variant variant : history.base()) {
				commit(origin, new ModelCommit("c" + c, history.configurations().get(c), variant));
				c++;
			}
			repositories.put(Side.FORK, network ? forkOverTheNetwork(origin, tmp.resolve("fork"), history.forkExcluded())
					: fork(origin, tmp.resolve("fork"), history.forkExcluded()));
			if (mergeCommit == MergeCommit.AFTER_FORK)
				addMergeCommit(origin);
			for (Action action : history.actions()) {
				Path side = repositories.get(action.side());
				Path other = repositories.get(action.side().other());
				switch (action.kind()) {
					case COMMIT -> {
						commit(side, new ModelCommit("c" + c, history.configurations().get(c), action.variant()));
						c++;
					}
					case PULL -> {
						if (network)
							pullOverTheNetwork(side, other, action.excluded());
						else
							pull(side, other, action.excluded());
					}
					case PUSH -> {
						if (network)
							pushOverTheNetwork(side, other, action.excluded());
						else
							push(side, other, action.excluded());
					}
				}
			}
		}

		@Override
		public void close() throws IOException {
			FileUtils.deleteDirectory(tmp.toFile());
		}
	}

	/**
	 * Every revision the [sender] has of [feature], as a fork, pull or push names the revisions it
	 * leaves out - none if it has no such feature, or none is named
	 */
	static String revisionsOf(Path sender, String feature) {
		if (feature == null)
			return "";
		try (EccoService service = SyncHistory.open(sender)) {
			return service.getRepository().getFeatures().stream()
					.filter(f -> f.getName().equals(feature))
					.flatMap(f -> f.getRevisions().stream())
					.map(revision -> feature + "." + revision.getId())
					.collect(Collectors.joining(", "));
		}
	}

	static Path fork(Path origin, Path dir, String excluded) throws IOException {
		Files.createDirectories(dir);
		String revisions = revisionsOf(origin, excluded);
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(dir.resolve(".ecco"));
			if (revisions.isEmpty())
				service.fork(origin.resolve(".ecco"));
			else
				service.fork(origin.resolve(".ecco"), revisions);
		}
		return dir;
	}

	static void pull(Path repository, Path from, String excluded) {
		String revisions = revisionsOf(from, excluded);
		try (EccoService service = SyncHistory.open(repository)) {
			if (service.getRemotes().stream().noneMatch(remote -> remote.getName().equals("from")))
				service.addRemote("from", from.toString(), at.jku.isse.ecco.core.Remote.Type.LOCAL);
			service.pull("from", revisions);
		}
	}

	static void push(Path repository, Path to, String excluded) {
		String revisions = revisionsOf(repository, excluded);
		try (EccoService service = SyncHistory.open(repository)) {
			if (service.getRemotes().stream().noneMatch(remote -> remote.getName().equals("to")))
				service.addRemote("to", to.toString(), at.jku.isse.ecco.core.Remote.Type.LOCAL);
			service.push("to", revisions);
		}
	}

	/** a new name for each server: a server's port changes from one sync to the next */
	private static final java.util.concurrent.atomic.AtomicInteger SERVERS = new java.util.concurrent.atomic.AtomicInteger();

	static Path forkOverTheNetwork(Path origin, Path dir, String excluded) throws Exception {
		Files.createDirectories(dir);
		String revisions = revisionsOf(origin, excluded);
		SyncHistory.withServer(origin, port -> {
			try (EccoService service = new EccoService()) {
				service.setRepositoryDir(dir.resolve(".ecco"));
				service.fork("localhost", port, revisions);
			}
		});
		return dir;
	}

	static void pullOverTheNetwork(Path repository, Path from, String excluded) throws Exception {
		String revisions = revisionsOf(from, excluded);
		SyncHistory.withServer(from, port -> {
			try (EccoService service = SyncHistory.open(repository)) {
				String name = "server" + SERVERS.incrementAndGet();
				service.addRemote(name, "localhost:" + port, at.jku.isse.ecco.core.Remote.Type.REMOTE);
				service.pull(name, revisions);
			}
		});
	}

	static void pushOverTheNetwork(Path repository, Path to, String excluded) throws Exception {
		String revisions = revisionsOf(repository, excluded);
		SyncHistory.withServer(to, port -> {
			try (EccoService service = SyncHistory.open(repository)) {
				String name = "server" + SERVERS.incrementAndGet();
				service.addRemote(name, "localhost:" + port, at.jku.isse.ecco.core.Remote.Type.REMOTE);
				service.push(name, revisions);
			}
		});
	}

	static Path init(Path dir, Integer maxOrder) {
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(dir.resolve(".ecco"));
			service.init();
		}
		if (maxOrder != null) {
			try (EccoService service = SyncHistory.open(dir)) {
				((Repository.Op) service.getRepository()).setMaxOrder(maxOrder);
				service.store();
			}
		}
		return dir;
	}

	/** writes the variant's files, and only them, into the repository's working directory and commits them */
	static void commit(Path repository, ModelCommit commit) throws IOException {
		Path work = repository.resolveSibling(repository.getFileName() + "-work");
		FileUtils.deleteDirectory(work.toFile());
		Files.createDirectories(work);
		for (Map.Entry<String, List<String>> file : commit.variant().files().entrySet())
			Files.writeString(work.resolve(file.getKey()), String.join("\n", file.getValue()) + "\n");
		try (EccoService service = SyncHistory.open(repository)) {
			service.setBaseDir(work);
			service.commit(commit.message(), commit.configuration());
		}
	}

	/** each configuration -> the text files its checkout writes */
	static Map<String, SortedMap<String, List<String>>> checkouts(Path repository, Collection<String> configurations, Path dir) throws IOException {
		Map<String, SortedMap<String, List<String>>> result = new TreeMap<>();
		int i = 0;
		try (EccoService service = SyncHistory.open(repository)) {
			for (String configuration : new TreeSet<>(configurations)) {
				Path checkout = Files.createDirectories(dir.resolve("" + i++));
				service.setBaseDir(checkout);
				service.checkout(configuration);
				SortedMap<String, List<String>> files = new TreeMap<>();
				try (Stream<Path> written = Files.list(checkout)) {
					for (Path file : written.filter(f -> f.getFileName().toString().endsWith(".txt")).toList())
						files.put(file.getFileName().toString(), Files.readAllLines(file));
				}
				result.put(configuration, files);
			}
		}
		return result;
	}

	/** the commits' messages, in the order the repository lists them */
	static List<String> messages(Path repository) {
		try (EccoService service = SyncHistory.open(repository)) {
			return service.getCommits().stream().map(Commit::getCommitMessage).toList();
		}
	}

	/**
	 * Each commit's message -> its configuration with the revisions' full ids. A feature made after the
	 * fork on both sides is one feature with a revision from each (FR-D3f), so a name alone does not
	 * say which revision a commit had.
	 */
	static Map<String, String> configurations(Path repository) {
		Map<String, String> result = new TreeMap<>();
		try (EccoService service = SyncHistory.open(repository)) {
			for (Commit commit : service.getCommits())
				if (commit.getConfiguration() != null) // a merge commit has none
					result.put(commit.getCommitMessage(), Arrays.stream(commit.getConfiguration().getFeatureRevisions())
						.map(revision -> revision.getFeature().getName() + "." + revision.getId())
							.sorted().collect(Collectors.joining(", ")));
		}
		return result;
	}

	static int maxOrder(Path repository) {
		try (EccoService service = SyncHistory.open(repository)) {
			return ((Repository.Op) service.getRepository()).getMaxOrder();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Properties

	/**
	 * Every configuration committed once, the maximum order at the number of features: each repository
	 * holds the model's commits, once each, and checks every one of them out as it was committed.
	 */
	@Property(tries = 300)
	void everyHeldCommitChecksOutAsCommitted(@ForAll("onceEach") History history) throws Exception {
		checkEveryHeldCommit(history, false);
	}

	/** As {@link #everyHeldCommitChecksOutAsCommitted}, every fork, pull and push from a sync server over a loopback socket. */
	@Property(tries = 300)
	void everyHeldCommitChecksOutAsCommittedOverTheNetwork(@ForAll("onceEach") History history) throws Exception {
		checkEveryHeldCommit(history, true);
	}

	private void checkEveryHeldCommit(History history, boolean network) throws Exception {
		Map<Side, List<ModelCommit>> model = model(history);
		collectSynced(history, model);
		try (Run run = new Run()) {
			run.network = network;
			run.play(history, FEATURES.size());
			for (Side side : Side.values()) {
				Path repository = run.repositories.get(side);
				assertEquals(FEATURES.size(), maxOrder(repository), side + "'s maximum order");

				List<String> messages = messages(repository);
				assertEquals(messages.size(), new HashSet<>(messages).size(), side + " holds a commit twice: " + messages);
				assertEquals(model.get(side).stream().map(ModelCommit::message).collect(Collectors.toSet()), new HashSet<>(messages),
						side + "'s commits");

				// every commit checked out by its own configuration, revisions and all
				Map<String, String> configurations = configurations(repository);
				Map<String, SortedMap<String, List<String>>> actual = checkouts(repository, configurations.values(), run.tmp.resolve("co-" + side));
				for (ModelCommit commit : model.get(side))
					assertEquals(commit.variant().files(), actual.get(configurations.get(commit.message())),
							side + "'s checkout of " + commit.message() + " [" + configurations.get(commit.message()) + "]");
			}
		}
	}

	/**
	 * FR-D3b/c with configurations committed again with other lines, at the default maximum order:
	 * each repository checks out every configuration it holds as a new repository does that has the
	 * model's commits committed in its order.
	 */
	@Property(tries = 300)
	void syncedRepositoriesCheckOutAsTheirCommitsInOrder(@ForAll("withRepeats") History history) throws Exception {
		checkInOrder(history, false);
	}

	/** As {@link #syncedRepositoriesCheckOutAsTheirCommitsInOrder}, every fork, pull and push from a sync server over a loopback socket. */
	@Property(tries = 300)
	void syncedRepositoriesCheckOutAsTheirCommitsInOrderOverTheNetwork(@ForAll("withRepeats") History history) throws Exception {
		checkInOrder(history, true);
	}

	private void checkInOrder(History history, boolean network) throws Exception {
		Map<Side, List<ModelCommit>> model = model(history);
		collectSynced(history, model);
		try (Run run = new Run()) {
			run.network = network;
			run.play(history, null);
			for (Side side : Side.values()) {
				Path repository = run.repositories.get(side);
				assertEquals(model.get(side).stream().map(ModelCommit::message).collect(Collectors.toSet()), new HashSet<>(messages(repository)),
						side + "'s commits");

				// the same commits, with the same revisions, committed in the model's order into a new repository
				Map<String, String> configurations = configurations(repository);
				Path inOrder = init(run.tmp.resolve("in-order-" + side), null);
				for (ModelCommit commit : model.get(side))
					commit(inOrder, new ModelCommit(commit.message(), configurations.get(commit.message()), commit.variant()));
				Set<String> held = new TreeSet<>(configurations.values());
				assertEquals(checkouts(inOrder, held, run.tmp.resolve("co-in-order-" + side)),
						checkouts(repository, held, run.tmp.resolve("co-" + side)),
						side + " checks out other than its commits committed in order");
			}
		}
	}

	/**
	 * Both sides have a merge commit, made before the fork: every commit one side lacks has a
	 * configuration, so pull and push commit them again (FR-D3e) and each repository checks every
	 * commit it holds out as committed. Until gap #96 was fixed, any merge commit made sync merge,
	 * and the merge counted the history both sides share twice: after [A, B, C]=l1, a fork, [A, D]=l1
	 * in the origin, [A, C]=l2 in the fork and the fork's pull and push, the origin checked its own
	 * first commit out without l1.
	 */
	@Property(tries = 300)
	void sharedMergeCommitsLeaveSyncToReplay(@ForAll("onceEachForkingAll") History history) throws Exception {
		checkEveryVariantAsCommitted(history, MergeCommit.BEFORE_FORK);
	}

	/**
	 * A merge commit only the origin has: a pull of the origin's commits, or a push to the fork,
	 * cannot replay it and merges (FR-D3e) - which still counts the history both sides share twice
	 * and loses lines (the rest of gap #96).
	 */
	@Property(tries = 300)
	@Disabled("gap #96: a merge (FR-D3e) still counts the history both sides share twice")
	void mergedRepositoriesCheckOutEveryVariantAsCommitted(@ForAll("onceEachNoFeatureNewOnBothSides") History history) throws Exception {
		checkEveryVariantAsCommitted(history, MergeCommit.AFTER_FORK);
	}

	/** each repository checks every commit the model says it has - its own, or the other side's taken in - out as committed */
	private void checkEveryVariantAsCommitted(History history, MergeCommit mergeCommit) throws Exception {
		Map<Side, List<ModelCommit>> model = model(history);
		collectSynced(history, model);
		try (Run run = new Run()) {
			run.mergeCommit = mergeCommit;
			run.play(history, FEATURES.size());
			Map<String, String> configurations = new TreeMap<>();
			for (Side side : Side.values())
				configurations.putAll(configurations(run.repositories.get(side)));
			for (Side side : Side.values()) {
				Path repository = run.repositories.get(side);
				List<ModelCommit> held = model.get(side);
				Map<String, SortedMap<String, List<String>>> actual = checkouts(repository,
						held.stream().map(c -> configurations.get(c.message())).toList(), run.tmp.resolve("co-" + side));
				for (ModelCommit commit : held)
					assertEquals(commit.variant().files(), actual.get(configurations.get(commit.message())),
							side + "'s checkout of " + commit.message() + " [" + configurations.get(commit.message()) + "]");
			}
		}
	}

	@Example
	void theModelAppendsWhatTheReceiverLacksInTheSendersOrder() {
		Variant v = new Variant(new TreeMap<>(Map.of("a.txt", List.of("l1"))));
		History history = new History(List.of(v), List.of(
				new Action(Kind.COMMIT, Side.ORIGIN, v), new Action(Kind.COMMIT, Side.FORK, v),
				new Action(Kind.PULL, Side.FORK, null), new Action(Kind.PUSH, Side.FORK, null)),
				List.of("A", "B", "C"));
		Map<Side, List<ModelCommit>> model = model(history);
		assertEquals(List.of("c0", "c2", "c1"), model.get(Side.FORK).stream().map(ModelCommit::message).toList());
		assertEquals(List.of("c0", "c1", "c2"), model.get(Side.ORIGIN).stream().map(ModelCommit::message).toList());
	}
}
