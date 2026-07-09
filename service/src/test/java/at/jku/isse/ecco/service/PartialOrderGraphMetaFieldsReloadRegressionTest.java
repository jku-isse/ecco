package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for a real checkout-correctness bug found via a user's real repository (see
 * memory/association-condition-corruption-after-reload for the full investigation) - not the same
 * bug as {@link TreeFusionOrderedSiblingMatchingTest}/{@link PartialOrderGraphReloadRegressionTest},
 * which are separate, already-fixed bugs found earlier in the same investigation chain.
 * <p>
 * {@code examples/lilypond_meta_fields}: v1 commits 5 quoted-string metadata fields; v2 adds 4 more,
 * inserted in the MIDDLE of the existing ones (not appended at the end).
 * <p>
 * Committing v1, then closing and reopening the repository (this step matters - see
 * {@link #reopenBetweenCommits_isCorrect()} vs {@link #singleSession_isCorrect()}), then committing
 * v2 and checking out "meta.1, header.1" used to corrupt one field: a quoted string value got
 * appended onto an unrelated field's value instead of staying on its own line (non-compilable
 * lilypond). Root cause: {@code SerModuleCounter}/{@code SerModuleRevisionCounter} held direct
 * (non-transient) references to {@code Module}/{@code ModuleRevision} objects, which carry a
 * mutable count field read by {@code Association.computeCondition()} to decide whether a module was
 * "always present". Since associations are persisted as separate files, each one independently
 * re-serialized its own copy of a module/module-revision instead of sharing the repository's single
 * canonical instance (only correct by construction within one continuous session) - so after any
 * reload, that count diverged per association, producing an over-broad presence condition that let
 * pruned content wrongly survive. Fixed in {@code SerTransactionStrategy.resolveModuleReferences()},
 * a post-load pass that replaces each counter's reference with the repository's own canonical
 * lookup (no id surrogate needed - {@code Module}/{@code ModuleRevision} equality is already
 * content-based).
 */
public class PartialOrderGraphMetaFieldsReloadRegressionTest {

	private static final Path EXAMPLES_DIR = LilypondVariantsCommitCheckoutTest.findRepoRoot()
			.resolve("examples").resolve("lilypond_meta_fields");
	private static final String FILE_NAME = "meta.ly";

	@Test
	@Timeout(30)
	public void singleSession_isCorrect() throws IOException {
		Path repoDir = Files.createTempDirectory("meta-fields-single").resolve(".ecco");

		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.init();
			service.setBaseDir(EXAMPLES_DIR.resolve("v1_meta"));
			service.commit("v1", "meta.1");

			service.setBaseDir(EXAMPLES_DIR.resolve("v2_meta_header"));
			service.commit("v2", "meta.1, header.1");

			assertCorrect(checkout(service));
		}
	}

	@Test
	@Timeout(30)
	public void reopenBetweenCommits_isCorrect() throws IOException {
		Path repoDir = Files.createTempDirectory("meta-fields-reopen").resolve(".ecco");

		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.init();
			service.setBaseDir(EXAMPLES_DIR.resolve("v1_meta"));
			service.commit("v1", "meta.1");
		}

		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repoDir);
			service.open();
			service.setBaseDir(EXAMPLES_DIR.resolve("v2_meta_header"));
			service.commit("v2", "meta.1, header.1");

			assertCorrect(checkout(service));
		}
	}

	private String checkout(EccoService service) throws IOException {
		Path checkoutDir = Files.createTempDirectory("meta-fields-checkout");
		service.setBaseDir(checkoutDir);
		service.checkout("meta.1, header.1");
		return Files.readString(checkoutDir.resolve(FILE_NAME), StandardCharsets.UTF_8);
	}

	private void assertCorrect(String content) {
		String expected = "pieceDuration = \"duration:90\"\n"
				+ "pieceTitle = \"Nachtwache II\"\n"
				+ "pieceComposer = \"Johannes Brahms\"\n"
				+ "pieceSubtitle = \"Aus Funf Gesange\"\n"
				+ "piecePoet = \"Friedrich Ruckert\"\n"
				+ "pieceSubject = \"Abendlied\"\n"
				+ "pieceLicense = \"free\"\n"
				+ "pieceArranger = \"\"\n"
				+ "pieceVoices = \"SAATTB\"";
		assertTrue(content.contains("piecePoet = \"Friedrich Ruckert\"") && !content.contains("Ruckert\"\"duration"),
				"piecePoet's value should not have duration:90 appended to it - got:\n" + content);
		assertEquals(expected.replaceAll("\\s+", ""), content.replaceAll("\\s+", ""));
	}
}
