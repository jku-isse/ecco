package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Variants that differ only in how the same music is spelled (see examples/lilypond_respelled):
 * a feature committed in one spelling must still compose with one committed in another. The
 * check is by music (lymusic, as in LilypondVariantsCommitCheckoutTest), since the point is
 * precisely that the text differs.
 */
public class LilypondRespelledVariantsTest {

	private static final Path ROOT = LilypondVariantsCommitCheckoutTest.findRepoRoot().resolve("examples");
	private static final Path VARIANTS = ROOT.resolve("lilypond_variants");
	private static final Path RESPELLED = ROOT.resolve("lilypond_respelled");
	private static final String FILE_NAME = "dieu.ly";

	@ParameterizedTest
	@ValueSource(strings = {"dynamics", "dynamics_indent", "dynamics_durations", "dynamics_relative"})
	@Timeout(300)
	public void aFeatureCommittedInAnotherSpelling_composes(String dynamicsVariant) throws IOException, InterruptedException {
		assumeTrue(Lymusic.available(), Lymusic.NEEDS);

		EccoService service = new EccoService();
		service.setRepositoryDir(Files.createTempDirectory("lilypond-respelled-repo").resolve(".ecco"));
		service.init();
		commit(service, VARIANTS.resolve("v1_setup"), "setup.1");
		commit(service, VARIANTS.resolve("v2_setup_notes"), "setup.1, notes.1");
		commit(service, VARIANTS.resolve("v3_setup_notes_articulation"), "setup.1, notes.1, articulation.1");
		commit(service, RESPELLED.resolve(dynamicsVariant), "setup.1, notes.1, dynamics.1");

		Path checkoutDir = Files.createTempDirectory("lilypond-respelled-checkout");
		service.setBaseDir(checkoutDir);
		service.checkout("setup.1, notes.1, articulation.1, dynamics.1");
		Path composed = checkoutDir.resolve(FILE_NAME);
		service.close();

		Path notesOnly = VARIANTS.resolve("v2_setup_notes").resolve(FILE_NAME);
		Lymusic.assertSameMusic(notesOnly, composed, "notes,lyrics,slurs,attributes", dynamicsVariant);
		Lymusic.assertSameMusic(VARIANTS.resolve("v3_setup_notes_articulation").resolve(FILE_NAME), composed,
				"articulations", dynamicsVariant);
		Lymusic.assertSameMusic(RESPELLED.resolve(dynamicsVariant).resolve(FILE_NAME), composed, "dynamics", dynamicsVariant);
	}

	/**
	 * The tokens traced to dynamics.1 are the dynamics, however the rest is spelled: a re-indented
	 * variant (line breaks compare without their indentation) always; implicit durations and another
	 * \relative anchor with musical tokens (-Decco.lilypond.musicalTokens=true and lybar through
	 * LYPYTHON), where each note is compared by its absolute pitch and duration. Before either, the
	 * re-indented variant traced 86 tokens to dynamics.1 where the dynamics are 24.
	 */
	@Test
	@Timeout(300)
	public void aRespelledVariantTracesOnlyItsFeature() throws IOException {
		assumeTrue(LilypondTraceMeasure.readable(), LilypondTraceMeasure.UNREADABLE);
		int dynamics = tracedToDynamics("dynamics");
		assertEquals(dynamics, tracedToDynamics("dynamics_indent"), "re-indented");
		assumeTrue(LilypondTraceMeasure.musicalTokens() && Lymusic.available(),
				"implicit durations and \\relative anchors need musical tokens (the default for a new repository) and lymodel");
		assertEquals(dynamics, tracedToDynamics("dynamics_durations"), "durations left implicit");
		assertEquals(dynamics, tracedToDynamics("dynamics_relative"), "another \\relative anchor");
	}

	private static int tracedToDynamics(String dynamicsVariant) throws IOException {
		EccoService service = LilypondTraceMeasure.commitAfter(VARIANTS, 3, RESPELLED.resolve(dynamicsVariant),
				"setup.1, notes.1, dynamics.1");
		int traced = LilypondTraceMeasure.tokensTracedTo(service, "dynamics").size();
		service.close();
		return traced;
	}

	private static void commit(EccoService service, Path dir, String configuration) {
		service.setBaseDir(dir);
		service.commit(dir.getFileName().toString(), configuration);
	}
}
