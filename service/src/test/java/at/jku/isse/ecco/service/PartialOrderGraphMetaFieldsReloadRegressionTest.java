package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minimal, portable repro of a real checkout-correctness bug found via a user's real repository
 * (see memory/pog-align-duplicate-candidate-ambiguity for the full investigation) - NOT the same bug
 * as {@link TreeFusionOrderedSiblingMatchingTest}/{@link PartialOrderGraphReloadRegressionTest},
 * which are both fixed. This one is characterized but NOT fixed.
 * <p>
 * {@code examples/lilypond_meta_fields}: v1 commits 5 quoted-string metadata fields; v2 adds 4 more,
 * inserted in the MIDDLE of the existing ones (not appended at the end) - every field's value shares
 * the same generic "LilyPond.list" wrapper artifact type, so they're all mutually content-equal to
 * each other from {@code PartialOrderGraph.align()}'s point of view, differing only by position.
 * <p>
 * Committing v1, then closing and reopening the repository (this step matters - see
 * {@link #reopenBetweenCommits_producesGarbledOutput()} vs {@link #singleSession_isCorrect()} - the
 * single-session case is fine), then committing v2 and checking out "meta.1, header.1" corrupts one
 * field: a quoted string value gets appended onto an unrelated field's value instead of staying on
 * its own line (non-compilable lilypond). Root cause is narrowed to
 * {@code PartialOrderGraph.align()}'s {@code iterativeLcsAlignment()} assigning the wrong one of
 * several content-equal candidates a matching sequence number - confirmed via targeted
 * instrumentation on this exact repro - but multiple attempts to isolate it further into a fast,
 * pure {@code PartialOrderGraph}-only unit test (including an actual Java serialization round trip
 * simulating the reload) did not reproduce it, meaning something about the FULL repository reload
 * (not just the POG object) is part of the trigger. Left disabled rather than fixed - a real fix
 * touches core POG matching logic in a subsystem with a history of subtle correctness bugs
 * ({@code directPoaAlignment}'s own javadoc, {@code pog-merge-shared-artifact-bug}), and deserves a
 * dedicated session with its own fast feedback loop, which this test at least now provides the
 * fixture for.
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
	@Disabled("Characterized, not fixed - see class javadoc and memory/pog-align-duplicate-candidate-ambiguity")
	public void reopenBetweenCommits_producesGarbledOutput() throws IOException {
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
