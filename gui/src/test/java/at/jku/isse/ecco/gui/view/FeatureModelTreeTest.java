package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link FeatureModelTree} has no JavaFX/GraphStream dependency specifically so it can be exercised
 * like this, against a real repository, without needing to drive the actual Feature Model tab.
 * <p>
 * Regression coverage for two real defects found and fixed after initial review:
 * <ol>
 *     <li>Picking whichever earlier feature a given feature co-occurs with <em>most</em> collapsed
 *     every incrementally-built repository into a star (everything attaching straight to the very
 *     first feature), since later features co-occur with all their transitive prerequisites, not
 *     just their immediate one. Picking the <em>nearest</em> (most recently introduced) co-occurring
 *     predecessor instead recovers the real chain - {@link #cumulativeHistory_producesAFlatChainOfImmediatePredecessors()}.</li>
 *     <li>Even with that fixed, depth still tracked chain length one-to-one, so a long purely
 *     sequential rollout still rendered as a deep staircase despite having no actual branching.
 *     Depth now only steps down at a real branch point (a feature with more than one feature built
 *     directly on it) - {@link #divergingFeatures_bothBuiltDirectlyOnSameBase_stepDownOneLevelTogether()}.</li>
 * </ol>
 */
public class FeatureModelTreeTest {

	private static final Path EXAMPLES_DIR = findRepoRoot().resolve("examples").resolve("lilypond_variants");

	private static final List<String> VARIANT_DIRS = List.of(
			"v1_setup", "v2_setup_notes", "v3_setup_notes_articulation",
			"v4_setup_notes_articulation_lyrics", "v5_setup_notes_articulation_lyrics_slurs",
			"v6_setup_notes_articulation_lyrics_dynamics");

	// strictly cumulative on purpose (each commit's configuration is a superset of the last), so
	// every feature co-occurs with every earlier one - the "nearest predecessor wins" rule (see
	// FeatureModelTree's class javadoc) is exactly what turns that into a clean chain instead of
	// everything fanning out from "setup"
	private static final List<String> CONFIGURATIONS = List.of(
			"setup.1", "setup.1, notes.1", "setup.1, notes.1, articulation.1",
			"setup.1, notes.1, articulation.1, lyrics.1",
			"setup.1, notes.1, articulation.1, lyrics.1, slurs.1",
			"setup.1, notes.1, articulation.1, lyrics.1, slurs.1, dynamics.1");

	private static final List<String> FEATURE_CHAIN = List.of("setup", "notes", "articulation", "lyrics", "slurs", "dynamics");

	@Test
	@Timeout(30)
	public void cumulativeHistory_producesAFlatChainOfImmediatePredecessors() throws IOException {
		Path repoDir = Files.createTempDirectory("feature-model-tree-test").resolve(".ecco");

		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.init();
			for (int i = 0; i < VARIANT_DIRS.size(); i++) {
				service.setBaseDir(EXAMPLES_DIR.resolve(VARIANT_DIRS.get(i)));
				service.commit(VARIANT_DIRS.get(i), CONFIGURATIONS.get(i));
			}

			List<FeatureModelTree.Placement> placements = FeatureModelTree.compute(service.getRepository());

			assertEquals(FEATURE_CHAIN.size(), placements.size(), "one placement per feature");

			Map<String, FeatureModelTree.Placement> byName = placements.stream()
					.collect(Collectors.toMap(p -> p.feature.getName(), p -> p));
			assertEquals(Set.copyOf(FEATURE_CHAIN), byName.keySet());

			FeatureModelTree.Placement root = byName.get(FEATURE_CHAIN.get(0));
			assertNull(root.parent, "the first-introduced feature must be a root");
			assertEquals(0, root.depth);

			// each feature should attach to its immediate predecessor in the chain, not to "setup"
			// directly - but since every link in this chain has exactly one child (no branching),
			// depth should stay flat at 0 throughout rather than increasing at every link
			for (int i = 1; i < FEATURE_CHAIN.size(); i++) {
				FeatureModelTree.Placement placement = byName.get(FEATURE_CHAIN.get(i));
				Feature expectedParent = byName.get(FEATURE_CHAIN.get(i - 1)).feature;
				assertEquals(expectedParent, placement.parent, FEATURE_CHAIN.get(i) + " should attach to its immediate predecessor " + FEATURE_CHAIN.get(i - 1));
				assertEquals(0, placement.depth, FEATURE_CHAIN.get(i) + " is a single-child continuation, not a branch, so should stay at depth 0");
			}

			// regression check: every node in the chain sits at depth 0, so without its own distinct
			// x it would render at the exact same coordinates as its neighbors and be hidden behind
			// them - only one of the six nodes would actually be visible
			long distinctXCount = placements.stream().map(p -> p.x).distinct().count();
			assertEquals(placements.size(), distinctXCount, "every node in a flat chain must still get its own distinct x position");
		}
	}

	@Test
	@Timeout(30)
	public void divergingFeatures_bothBuiltDirectlyOnSameBase_stepDownOneLevelTogether() throws IOException {
		Path workDir = Files.createTempDirectory("feature-model-tree-branch-test");
		Path repoDir = workDir.resolve(".ecco");

		Path coreDir = workDir.resolve("core");
		Files.createDirectories(coreDir);
		Files.writeString(coreDir.resolve("core.txt"), "core\n");

		Path branchADir = workDir.resolve("branchA");
		Files.createDirectories(branchADir);
		Files.writeString(branchADir.resolve("core.txt"), "core\n");
		Files.writeString(branchADir.resolve("branchA.txt"), "branch A\n");

		Path branchBDir = workDir.resolve("branchB");
		Files.createDirectories(branchBDir);
		Files.writeString(branchBDir.resolve("core.txt"), "core\n");
		Files.writeString(branchBDir.resolve("branchB.txt"), "branch B\n");

		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.init();

			service.setBaseDir(coreDir);
			service.commit("core", "Core");

			service.setBaseDir(branchADir);
			service.commit("branch A", "Core, BranchA");

			service.setBaseDir(branchBDir);
			service.commit("branch B", "Core, BranchB");

			Map<String, FeatureModelTree.Placement> byName = FeatureModelTree.compute(service.getRepository()).stream()
					.collect(Collectors.toMap(p -> p.feature.getName(), p -> p));

			FeatureModelTree.Placement core = byName.get("Core");
			FeatureModelTree.Placement branchA = byName.get("BranchA");
			FeatureModelTree.Placement branchB = byName.get("BranchB");

			assertNull(core.parent);
			assertEquals(0, core.depth);

			// BranchA and BranchB never co-occur with each other (different files, never committed
			// together) - only with Core - so both attach directly to Core, which now has two
			// children: a real branch point, so both should step down exactly one level from Core,
			// not stay flat with it and not stack on top of each other
			assertEquals(core.feature, branchA.parent);
			assertEquals(core.feature, branchB.parent);
			assertEquals(1, branchA.depth);
			assertEquals(1, branchB.depth);
			assertNotEquals(branchA.x, branchB.x, "two distinct children of the same branch must not overlap horizontally either");
		}
	}

	/**
	 * Regression for the fix that made {@link FeatureModelTree#compute} read co-occurrence from real
	 * commit configurations instead of {@code Association#computeCondition()}'s module lattice (see
	 * {@code CONSTRAINT_MINING_DESIGN.md}'s "Surplus-module suppression" section): that lattice
	 * accumulates redundant terms per association (not just the minimal necessary one), and a real
	 * co-occurrence pair whose only lattice-level evidence came from those redundant terms would
	 * silently vanish if the lattice were ever pruned -- confirmed by direct measurement during that
	 * investigation. Reading commit configurations directly is immune to that by construction. This
	 * reuses the same repo shape (a mandatory-like base feature plus a feature committed only
	 * alongside it, several times) that produced the redundant lattice in the original investigation.
	 */
	@Test
	@Timeout(30)
	public void coOccurrenceSurvivesEvenThoughTheAssociationsHaveRedundantLatticeTerms() throws IOException {
		Path workDir = Files.createTempDirectory("feature-model-tree-cooccurrence-test");
		Path repoDir = workDir.resolve(".ecco");

		Path commonDir = workDir.resolve("common");
		Files.createDirectories(commonDir);
		Files.writeString(commonDir.resolve("common.txt"), "base\n");

		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.init();

			service.setBaseDir(commonDir);
			service.commit("base", "Common");

			for (int i = 1; i <= 4; i++) {
				Path p = workDir.resolve("old-" + i);
				Files.createDirectories(p);
				Files.writeString(p.resolve("common.txt"), "old-variant\n");
				Files.writeString(p.resolve("old.txt"), "old only\n");
				Files.writeString(p.resolve("old-extra-" + i + ".txt"), "old pad " + i + "\n");
				service.setBaseDir(p);
				service.commit("old " + i, "Common, Old");
			}

			Map<String, FeatureModelTree.Placement> byName = FeatureModelTree.compute(service.getRepository()).stream()
					.collect(Collectors.toMap(p -> p.feature.getName(), p -> p));

			FeatureModelTree.Placement common = byName.get("Common");
			FeatureModelTree.Placement old = byName.get("Old");

			assertNull(common.parent, "Common was introduced first and has no earlier co-occurring feature");
			assertEquals(common.feature, old.parent,
					"Old must attach to Common, its real (repeatedly-committed) co-occurring predecessor -- "
							+ "this is exactly the pair whose lattice-level presence disappeared entirely under "
							+ "absorption in the original investigation");
		}
	}

	static Path findRepoRoot() {
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null) {
			if (Files.exists(dir.resolve("settings.gradle"))) {
				return dir;
			}
			dir = dir.getParent();
		}
		throw new IllegalStateException("Could not locate repository root (no settings.gradle found in any ancestor directory).");
	}
}
