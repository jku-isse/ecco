package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for a real bug reported by a user: after init-ing a repository and committing
 * many folders within one continuous session, the Artifacts tab's associations looked fine, but
 * selecting a configuration (Live Features) failed to update the tree and made the UI feel
 * unresponsive - yet the exact same repository worked correctly after quitting and reopening it.
 * <p>
 * Root cause: {@code Repository.addNegativeFeatureModules(Feature)} - called whenever a genuinely
 * new feature is introduced mid-history (e.g. a "dynamics" feature added several commits after
 * "setup"/"notes") - retroactively adds a "feature absent" observation
 * ({@code association.addObservation(...)}) to EVERY existing association in the repository that
 * already had an observation for the corresponding pre-negation module, not just associations
 * touched by the current commit. This is correct, intentional behavior (an association that was
 * always present before a feature existed is trivially always present when that feature is absent
 * too), but nothing marked the mutated association dirty for re-persistence - only associations
 * added/removed via {@code addAssociation()}/{@code removeAssociation()} during
 * {@code Repository.extract()}'s own slicing loop were. So within a live session the mutation was
 * visible immediately (a growing, correct - if verbose - presence condition), but silently lost on
 * the next reload: the reloaded condition came back missing those retroactively-added "!feature"
 * terms, making it too broad (not just shorter), which is what caused {@code holds(configuration)}
 * checks (used by "Select by Configuration" and the Live Features panel) to behave differently
 * before vs. after a restart. Same underlying defect class as
 * {@link OriginalAssociationDirtyTrackingRegressionTest} - mutating an association's counter
 * in-memory without marking it dirty - just a different call site.
 * <p>
 * Fixed by marking the association dirty again (idempotent, see
 * {@code SerRepository.addAssociation()}) right after the retroactive observation is added in
 * {@code Repository.addNegativeFeatureModules()}.
 */
public class NegativeFeatureModuleDirtyTrackingRegressionTest {

	private static final Path EXAMPLES_DIR = LilypondVariantsCommitCheckoutTest.findRepoRoot()
			.resolve("examples").resolve("lilypond_variants");

	private static final List<String> VARIANT_DIRS = List.of(
			"v1_setup", "v2_setup_notes", "v3_setup_notes_articulation",
			"v4_setup_notes_articulation_lyrics", "v5_setup_notes_articulation_lyrics_slurs",
			"v6_setup_notes_articulation_lyrics_dynamics");

	private static final List<String> CONFIGURATIONS = List.of(
			"setup.1", "setup.1, notes.1", "setup.1, notes.1, articulation.1",
			"setup.1, notes.1, articulation.1, lyrics.1",
			"setup.1, notes.1, articulation.1, lyrics.1, slurs.1",
			"setup.1, notes.1, articulation.1, lyrics.1, slurs.1, dynamics.1");

	@Test
	@Timeout(30)
	public void reloadedConditionsMatchSameSessionConditions() throws IOException {
		Path repoDir = Files.createTempDirectory("negfeature-dirty-tracking").resolve(".ecco");

		TreeSet<String> sameSession = new TreeSet<>();
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.init();
			for (int i = 0; i < VARIANT_DIRS.size(); i++) {
				service.setBaseDir(EXAMPLES_DIR.resolve(VARIANT_DIRS.get(i)));
				service.commit(VARIANT_DIRS.get(i), CONFIGURATIONS.get(i));
			}
			for (var a : service.getRepository().getAssociations()) {
				sameSession.add(a.getRootNode().countArtifacts() + " :: " + a.computeCondition());
			}
		}

		TreeSet<String> reopened = new TreeSet<>();
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.open();
			for (var a : service.getRepository().getAssociations()) {
				reopened.add(a.getRootNode().countArtifacts() + " :: " + a.computeCondition());
			}
		}

		assertEquals(sameSession, reopened,
				"an association's presence condition should not change just from closing and reopening the repository");
	}
}
