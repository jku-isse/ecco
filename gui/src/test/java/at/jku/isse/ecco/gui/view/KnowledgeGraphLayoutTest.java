package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KnowledgeGraphLayout} has no JavaFX/GraphStream dependency specifically so it can be
 * exercised like this, against a real repository, without needing to drive the actual Knowledge
 * Graph tab -- same rationale as {@link FeatureModelTreeTest}.
 */
public class KnowledgeGraphLayoutTest {

	private static final Set<KnowledgeGraphLayout.EntityKind> ALL_KINDS = EnumSet.allOf(KnowledgeGraphLayout.EntityKind.class);

	/**
	 * Four commits: two distinct configurations ("A" and "C") each committed once, and one
	 * configuration ("A, B") committed twice back to back -- the repeat is what exercises
	 * {@code EccoService}'s commit-time auto-variant dedup (one {@code Variant} per distinct
	 * {@link at.jku.isse.ecco.feature.Configuration}, matched by {@code equals()}).
	 */
	private static EccoService buildFourCommitRepo(Path workDir) throws IOException {
		EccoService service = new EccoService();
		service.setRepositoryDir(workDir.resolve(".ecco"));
		service.init();

		Path dir1 = workDir.resolve("c1");
		Files.createDirectories(dir1);
		Files.writeString(dir1.resolve("a.txt"), "a\n");
		service.setBaseDir(dir1);
		service.commit("commit 1", "A");

		Path dir2 = workDir.resolve("c2");
		Files.createDirectories(dir2);
		Files.writeString(dir2.resolve("a.txt"), "a\n");
		Files.writeString(dir2.resolve("b.txt"), "b\n");
		service.setBaseDir(dir2);
		service.commit("commit 2", "A, B");

		Path dir3 = workDir.resolve("c3");
		Files.createDirectories(dir3);
		Files.writeString(dir3.resolve("a.txt"), "a\n");
		Files.writeString(dir3.resolve("b.txt"), "b changed\n");
		service.setBaseDir(dir3);
		service.commit("commit 3", "A, B");

		Path dir4 = workDir.resolve("c4");
		Files.createDirectories(dir4);
		Files.writeString(dir4.resolve("c.txt"), "c\n");
		service.setBaseDir(dir4);
		service.commit("commit 4", "C");

		service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "A", null);
		service.acceptConstraint(ConstraintMiner.Kind.REQUIRES, "B", "A");
		service.acceptConstraint(ConstraintMiner.Kind.EXCLUDES, "B", "C");

		return service;
	}

	@Test
	@Timeout(30)
	public void basicShape_onePlacementPerRealEntity() throws IOException {
		Path workDir = Files.createTempDirectory("kg-layout-basic-shape");
		try (EccoService service = buildFourCommitRepo(workDir)) {
			KnowledgeGraphLayout.Snapshot snapshot = KnowledgeGraphLayout.compute(service.getRepository(), ALL_KINDS, 10, false);

			assertEquals(3, countOf(snapshot, KnowledgeGraphLayout.EntityKind.FEATURE), "A, B, C");
			assertEquals(3, countOf(snapshot, KnowledgeGraphLayout.EntityKind.CONSTRAINT), "MANDATORY(A), REQUIRES(B,A), EXCLUDES(B,C)");
			assertEquals(4, countOf(snapshot, KnowledgeGraphLayout.EntityKind.COMMIT));
			// commit 2 and commit 3 share a configuration, so they must produce (and share) exactly one variant
			assertEquals(3, countOf(snapshot, KnowledgeGraphLayout.EntityKind.VARIANT), "configs A / A,B / C -> 3 distinct variants");
		}
	}

	@Test
	@Timeout(30)
	public void lanes_eachEntityKindSharesOneYAndLanesAreOrdered() throws IOException {
		Path workDir = Files.createTempDirectory("kg-layout-lanes");
		try (EccoService service = buildFourCommitRepo(workDir)) {
			KnowledgeGraphLayout.Snapshot snapshot = KnowledgeGraphLayout.compute(service.getRepository(), ALL_KINDS, 10, false);

			double featureY = oneYOf(snapshot, KnowledgeGraphLayout.EntityKind.FEATURE);
			double constraintY = oneYOf(snapshot, KnowledgeGraphLayout.EntityKind.CONSTRAINT);
			double commitY = oneYOf(snapshot, KnowledgeGraphLayout.EntityKind.COMMIT);
			double associationY = oneYOf(snapshot, KnowledgeGraphLayout.EntityKind.ASSOCIATION);
			double variantY = oneYOf(snapshot, KnowledgeGraphLayout.EntityKind.VARIANT);

			assertTrue(featureY > constraintY, "Feature lane above Constraint lane");
			assertTrue(constraintY > commitY, "Constraint lane above Commit lane");
			assertTrue(commitY > associationY, "Commit lane above Association lane");
			assertTrue(associationY > variantY, "Association lane above Variant lane");
		}
	}

	@Test
	@Timeout(30)
	public void commitToFeatureEdges_matchTheCommitsConfiguration() throws IOException {
		Path workDir = Files.createTempDirectory("kg-layout-commit-feature-edges");
		try (EccoService service = buildFourCommitRepo(workDir)) {
			KnowledgeGraphLayout.Snapshot snapshot = KnowledgeGraphLayout.compute(service.getRepository(), ALL_KINDS, 10, false);

			String commit1NodeId = nodeIdForLabelPrefix(snapshot, KnowledgeGraphLayout.EntityKind.COMMIT, commit1Id(service));
			Set<String> selectedFeatureLabels = snapshot.edges.stream()
					.filter(e -> e.kind == KnowledgeGraphLayout.EdgeKind.SELECTS && e.sourceId.equals(commit1NodeId))
					.map(e -> labelOf(snapshot, e.targetId))
					.collect(Collectors.toSet());
			assertEquals(Set.of("A"), selectedFeatureLabels, "commit 1's configuration only selects A");
		}
	}

	@Test
	@Timeout(30)
	public void constraintEdgeCounts_mandatoryOneRequiresExcludesTwo() throws IOException {
		Path workDir = Files.createTempDirectory("kg-layout-constraint-edges");
		try (EccoService service = buildFourCommitRepo(workDir)) {
			KnowledgeGraphLayout.Snapshot snapshot = KnowledgeGraphLayout.compute(service.getRepository(), ALL_KINDS, 10, false);

			long mandatoryEdges = snapshot.edges.stream().filter(e -> e.kind == KnowledgeGraphLayout.EdgeKind.MANDATORY).count();
			long requiresEdges = snapshot.edges.stream().filter(e -> e.kind == KnowledgeGraphLayout.EdgeKind.REQUIRES).count();
			long excludesEdges = snapshot.edges.stream().filter(e -> e.kind == KnowledgeGraphLayout.EdgeKind.EXCLUDES).count();

			assertEquals(1, mandatoryEdges, "MANDATORY only draws the featureA edge");
			assertEquals(2, requiresEdges, "REQUIRES draws both featureA and featureB edges");
			assertEquals(2, excludesEdges, "EXCLUDES draws both featureA and featureB edges");
		}
	}

	@Test
	@Timeout(30)
	public void sharedConfiguration_producesOneSharedVariantNotTwo() throws IOException {
		Path workDir = Files.createTempDirectory("kg-layout-shared-variant");
		try (EccoService service = buildFourCommitRepo(workDir)) {
			KnowledgeGraphLayout.Snapshot snapshot = KnowledgeGraphLayout.compute(service.getRepository(), ALL_KINDS, 10, false);

			long producesVariantEdges = snapshot.edges.stream().filter(e -> e.kind == KnowledgeGraphLayout.EdgeKind.PRODUCES_VARIANT).count();
			assertEquals(4, producesVariantEdges, "one Commit -> Variant edge per commit, even though two share a target");

			String commit2NodeId = nodeIdForLabelPrefix(snapshot, KnowledgeGraphLayout.EntityKind.COMMIT, commit2Id(service));
			String commit3NodeId = nodeIdForLabelPrefix(snapshot, KnowledgeGraphLayout.EntityKind.COMMIT, commit3Id(service));
			String variantOfCommit2 = targetOfProducesVariantEdge(snapshot, commit2NodeId);
			String variantOfCommit3 = targetOfProducesVariantEdge(snapshot, commit3NodeId);
			assertEquals(variantOfCommit2, variantOfCommit3, "commit 2 and commit 3 share a configuration, so they must share a variant node");
		}
	}

	@Test
	@Timeout(30)
	public void commitLimit_windowsCommitsAssociationsAndVariants_butNotFeaturesOrConstraints() throws IOException {
		Path workDir = Files.createTempDirectory("kg-layout-commit-limit");
		try (EccoService service = buildFourCommitRepo(workDir)) {
			KnowledgeGraphLayout.Snapshot fullSnapshot = KnowledgeGraphLayout.compute(service.getRepository(), ALL_KINDS, 10, false);
			KnowledgeGraphLayout.Snapshot windowedSnapshot = KnowledgeGraphLayout.compute(service.getRepository(), ALL_KINDS, 1, false);

			assertEquals(1, countOf(windowedSnapshot, KnowledgeGraphLayout.EntityKind.COMMIT), "only the most recent commit");
			assertEquals(countOf(fullSnapshot, KnowledgeGraphLayout.EntityKind.FEATURE), countOf(windowedSnapshot, KnowledgeGraphLayout.EntityKind.FEATURE),
					"Features are never windowed by commitLimit");
			assertEquals(countOf(fullSnapshot, KnowledgeGraphLayout.EntityKind.CONSTRAINT), countOf(windowedSnapshot, KnowledgeGraphLayout.EntityKind.CONSTRAINT),
					"Constraints are never windowed by commitLimit");

			// the most recent commit (commit 4, configuration "C") only touches its own association(s)
			// and its own variant -- association/variant nodes must cascade from that same window
			assertTrue(countOf(windowedSnapshot, KnowledgeGraphLayout.EntityKind.ASSOCIATION) < countOf(fullSnapshot, KnowledgeGraphLayout.EntityKind.ASSOCIATION));
			assertEquals(1, countOf(windowedSnapshot, KnowledgeGraphLayout.EntityKind.VARIANT), "only commit 4's own variant is in scope");
		}
	}

	@Test
	@Timeout(30)
	public void commitLimit_associationsCascadeCorrectlyEvenWhenCommitLaneIsDisabled() throws IOException {
		Path workDir = Files.createTempDirectory("kg-layout-commit-lane-disabled");
		try (EccoService service = buildFourCommitRepo(workDir)) {
			Set<KnowledgeGraphLayout.EntityKind> withoutCommitLane = EnumSet.copyOf(ALL_KINDS);
			withoutCommitLane.remove(KnowledgeGraphLayout.EntityKind.COMMIT);

			KnowledgeGraphLayout.Snapshot allCommitsSnapshot = KnowledgeGraphLayout.compute(service.getRepository(), withoutCommitLane, 10, false);
			KnowledgeGraphLayout.Snapshot windowedSnapshot = KnowledgeGraphLayout.compute(service.getRepository(), withoutCommitLane, 1, false);

			assertEquals(0, countOf(windowedSnapshot, KnowledgeGraphLayout.EntityKind.COMMIT), "Commit lane is disabled");
			assertEquals(0, countOf(allCommitsSnapshot, KnowledgeGraphLayout.EntityKind.COMMIT), "Commit lane is disabled");
			assertTrue(countOf(windowedSnapshot, KnowledgeGraphLayout.EntityKind.ASSOCIATION) < countOf(allCommitsSnapshot, KnowledgeGraphLayout.EntityKind.ASSOCIATION),
					"the commit-limit window still constrains which associations are in scope, even with the Commit lane hidden");
		}
	}

	@Test
	@Timeout(30)
	public void computedVariantAssociationEdges_offByDefaultOnWhenRequested() throws IOException {
		Path workDir = Files.createTempDirectory("kg-layout-computed-edges");
		try (EccoService service = buildFourCommitRepo(workDir)) {
			KnowledgeGraphLayout.Snapshot withoutComputed = KnowledgeGraphLayout.compute(service.getRepository(), ALL_KINDS, 10, false);
			KnowledgeGraphLayout.Snapshot withComputed = KnowledgeGraphLayout.compute(service.getRepository(), ALL_KINDS, 10, true);

			assertEquals(0, withoutComputed.edges.stream().filter(e -> e.kind == KnowledgeGraphLayout.EdgeKind.TOUCHES_COMPUTED).count());
			assertTrue(withComputed.edges.stream().anyMatch(e -> e.kind == KnowledgeGraphLayout.EdgeKind.TOUCHES_COMPUTED),
					"at least one variant's configuration should hold for at least one association's presence condition");
		}
	}

	@Test
	@Timeout(30)
	public void compute_isDeterministicAcrossRepeatedCallsOnUnchangedRepository() throws IOException {
		Path workDir = Files.createTempDirectory("kg-layout-determinism");
		try (EccoService service = buildFourCommitRepo(workDir)) {
			KnowledgeGraphLayout.Snapshot first = KnowledgeGraphLayout.compute(service.getRepository(), ALL_KINDS, 10, false);
			KnowledgeGraphLayout.Snapshot second = KnowledgeGraphLayout.compute(service.getRepository(), ALL_KINDS, 10, false);

			assertEquals(toPositionMap(first), toPositionMap(second), "re-rendering an unchanged repository must not jitter node positions");
			Set<String> firstEdgeIds = first.edges.stream().map(e -> e.id).collect(Collectors.toSet());
			Set<String> secondEdgeIds = second.edges.stream().map(e -> e.id).collect(Collectors.toSet());
			assertEquals(firstEdgeIds, secondEdgeIds);
		}
	}

	// ---- helpers ----

	private static long countOf(KnowledgeGraphLayout.Snapshot snapshot, KnowledgeGraphLayout.EntityKind kind) {
		return snapshot.nodes.stream().filter(n -> n.kind == kind).count();
	}

	private static double oneYOf(KnowledgeGraphLayout.Snapshot snapshot, KnowledgeGraphLayout.EntityKind kind) {
		Set<Double> ys = snapshot.nodes.stream().filter(n -> n.kind == kind).map(n -> n.y).collect(Collectors.toSet());
		assertEquals(1, ys.size(), "every node of kind " + kind + " must share one lane y");
		return ys.iterator().next();
	}

	private static String labelOf(KnowledgeGraphLayout.Snapshot snapshot, String nodeId) {
		return snapshot.nodes.stream().filter(n -> n.id.equals(nodeId)).findFirst().orElseThrow().label;
	}

	private static String nodeIdForLabelPrefix(KnowledgeGraphLayout.Snapshot snapshot, KnowledgeGraphLayout.EntityKind kind, String commitId) {
		String shortId = commitId.length() <= 7 ? commitId : commitId.substring(0, 7);
		return snapshot.nodes.stream()
				.filter(n -> n.kind == kind && n.label.equals(shortId))
				.map(n -> n.id)
				.findFirst()
				.orElseThrow(() -> new AssertionError("no " + kind + " node for commit " + commitId));
	}

	private static String targetOfProducesVariantEdge(KnowledgeGraphLayout.Snapshot snapshot, String commitNodeId) {
		return snapshot.edges.stream()
				.filter(e -> e.kind == KnowledgeGraphLayout.EdgeKind.PRODUCES_VARIANT && e.sourceId.equals(commitNodeId))
				.map(e -> e.targetId)
				.findFirst()
				.orElseThrow();
	}

	private static java.util.Map<String, List<Double>> toPositionMap(KnowledgeGraphLayout.Snapshot snapshot) {
		return snapshot.nodes.stream().collect(Collectors.toMap(n -> n.id, n -> List.of(n.x, n.y)));
	}

	private static String commitByOrdinal(EccoService service, int oneBasedOrdinal) {
		List<Commit> commitsByDate = service.getRepository().getCommits().stream()
				.sorted(java.util.Comparator.comparing(Commit::getDate))
				.collect(Collectors.toList());
		return commitsByDate.get(oneBasedOrdinal - 1).getId();
	}

	private static String commit1Id(EccoService service) {
		return commitByOrdinal(service, 1);
	}

	private static String commit2Id(EccoService service) {
		return commitByOrdinal(service, 2);
	}

	private static String commit3Id(EccoService service) {
		return commitByOrdinal(service, 3);
	}

}
