package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.lilypond.LilyEccoTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Notes inserted into the middle of voices (see examples/lilypond_edits): a feature that edits
 * notes must compose with one committed on the unedited notes, and be traced to exactly the
 * tokens it changed.
 */
public class LilypondEditedVariantsTest {

	private static final Path ROOT = LilypondVariantsCommitCheckoutTest.findRepoRoot().resolve("examples");
	private static final Path EDITS = ROOT.resolve("lilypond_edits");
	private static final String FILE_NAME = "dieu.ly";

	@Test
	@Timeout(300)
	public void anEditComposesWithAFeatureOnTheUneditedNotes() throws IOException, InterruptedException {
		assumeTrue(Lymusic.available(), Lymusic.NEEDS);
		EccoService service = commitEdit();
		Path checkoutDir = Files.createTempDirectory("lilypond-edits-checkout");
		service.setBaseDir(checkoutDir);
		service.checkout("setup.1, notes.1, articulation.1, edit.1");
		service.close();
		Lymusic.assertSameMusic(EDITS.resolve("notes_articulation_edit").resolve(FILE_NAME), checkoutDir.resolve(FILE_NAME),
				"notes,lyrics,articulations,slurs,dynamics,attributes", "articulation + edit");
	}

	/** With musical tokens, the 7 inserted note tokens and the 3 they replace; plain tokens trace 14. */
	@Test
	@Timeout(300)
	public void anEditIsTracedToExactlyTheTokensItChanged() throws IOException {
		assumeTrue(LilyEccoTransformer.MUSICAL_TOKENS && Lymusic.available(),
				"needs -Decco.lilypond.musicalTokens=true and lymodel");
		EccoService service = commitEdit();
		int traced = LilypondTraceMeasure.tokensTracedTo(service, "edit").size();
		service.close();
		assertEquals(10, traced);
	}

	private static EccoService commitEdit() throws IOException {
		return LilypondTraceMeasure.commitAfter(ROOT.resolve("lilypond_variants"), 3, EDITS.resolve("notes_edit"),
				"setup.1, notes.1, edit.1");
	}
}
