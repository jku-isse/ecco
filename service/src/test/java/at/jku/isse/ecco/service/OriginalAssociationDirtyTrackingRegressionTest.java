package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for a real checkout-correctness bug found via a user's real repository (see
 * memory/association-condition-corruption-after-reload for the full investigation, and
 * PartialOrderGraphMetaFieldsReloadRegressionTest for a separate, already-fixed bug found earlier in
 * the same investigation).
 * <p>
 * {@code Repository.extract()}'s "ORIGINAL" handling slices the intersected part out of an existing
 * association's tree in place, but when that leaves the association non-empty (still has other,
 * unrelated content), nothing marked it dirty for re-persistence - only newly-created or fully-
 * emptied associations went through {@code addAssociation()}/{@code removeAssociation()}. So the
 * association's on-disk file kept its old, pre-slice content. Committing once more in the SAME
 * session masked this (the in-memory, already-reduced object was reused directly), but closing and
 * reopening the repository between commits reloaded the association at its stale, larger size - and
 * the next commit's slice redundantly re-sliced content it had already given away, producing
 * duplicate associations that ended up under-observed by the module-counter bookkeeping (empty
 * presence conditions) and let pruned content wrongly survive a checkout, duplicated onto the wrong
 * position.
 * <p>
 * Reproduces with three commits from {@code examples/lilypond_variants}, closing and reopening the
 * repository between every one (matching how a real multi-session GUI workflow naturally commits) -
 * a fourth commit added would have shown the same, but three is the minimal repro. Fixed in
 * {@code Repository.extract()} by calling {@code addAssociation()} again on an original association
 * whenever slicing actually reduced it (idempotent to call again - see
 * {@code SerRepository.addAssociation()}).
 */
public class OriginalAssociationDirtyTrackingRegressionTest {

	private static final Path EXAMPLES_DIR = LilypondVariantsCommitCheckoutTest.findRepoRoot()
			.resolve("examples").resolve("lilypond_variants");

	private static final List<String> VARIANT_DIRS = List.of("v1_setup", "v2_setup_notes", "v3_setup_notes_articulation");
	private static final List<String> CONFIGURATIONS = List.of("setup.1", "setup.1, notes.1", "setup.1, notes.1, articulation.1");

	@Test
	@Timeout(30)
	public void reopenBetweenEveryCommit_matchesSingleSessionAssociationShape() throws IOException {
		Path reopenRepoDir = Files.createTempDirectory("dirty-tracking-reopen").resolve(".ecco");
		for (int i = 0; i < VARIANT_DIRS.size(); i++) {
			try (EccoService service = new EccoService()) {
				service.setRepositoryDir(reopenRepoDir);
				if (i == 0) service.init(); else service.open();
				service.setBaseDir(EXAMPLES_DIR.resolve(VARIANT_DIRS.get(i)));
				service.commit(VARIANT_DIRS.get(i), CONFIGURATIONS.get(i));
			}
		}

		Path singleSessionRepoDir = Files.createTempDirectory("dirty-tracking-single").resolve(".ecco");
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(singleSessionRepoDir);
			service.init();
			for (int i = 0; i < VARIANT_DIRS.size(); i++) {
				service.setBaseDir(EXAMPLES_DIR.resolve(VARIANT_DIRS.get(i)));
				service.commit(VARIANT_DIRS.get(i), CONFIGURATIONS.get(i));
			}
		}

		assertEquals(shapeOf(singleSessionRepoDir), shapeOf(reopenRepoDir),
				"reopening between every commit should produce the same association shape as committing in one session");
	}

	@Test
	@Timeout(30)
	public void reopenBetweenEveryCommit_checkoutMatchesSource() throws IOException {
		Path repoDir = Files.createTempDirectory("dirty-tracking-checkout").resolve(".ecco");
		for (int i = 0; i < VARIANT_DIRS.size(); i++) {
			try (EccoService service = new EccoService()) {
				service.setRepositoryDir(repoDir);
				if (i == 0) service.init(); else service.open();
				service.setBaseDir(EXAMPLES_DIR.resolve(VARIANT_DIRS.get(i)));
				service.commit(VARIANT_DIRS.get(i), CONFIGURATIONS.get(i));
			}
		}

		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.open();
			Path checkoutDir = Files.createTempDirectory("dirty-tracking-checkout-out");
			service.setBaseDir(checkoutDir);
			service.checkout("setup.1, notes.1, articulation.1");

			String expected = normalize(Files.readString(EXAMPLES_DIR.resolve("v3_setup_notes_articulation").resolve("dieu.ly"), StandardCharsets.UTF_8));
			String actual = normalize(Files.readString(checkoutDir.resolve("dieu.ly"), StandardCharsets.UTF_8));
			assertEquals(expected, actual);
		}
	}

	private String normalize(String content) {
		return content.replaceAll("\\s+", "");
	}

	private List<Integer> shapeOf(Path repoDir) throws IOException {
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.open();
			var repo = service.getRepository();
			List<Integer> counts = new java.util.ArrayList<>();
			for (var a : repo.getAssociations()) {
				counts.add(a.getRootNode().countArtifacts());
			}
			counts.sort(Integer::compareTo);
			return counts;
		}
	}
}
