package at.jku.isse.ecco.service;

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
		assumeTrue(LilypondTraceMeasure.musicalTokens() && Lymusic.available(),
				"needs musical tokens (the default for a new repository) and lymodel");
		EccoService service = commitEdit();
		int traced = LilypondTraceMeasure.tokensTracedTo(service, "edit").size();
		service.close();
		assertEquals(10, traced);
	}

	/**
	 * A leap ({@code gis8 cis8 fis,8}) committed after the variant without it ({@code gis8 gis8 fis8}):
	 * with musical tokens both {@code fis} are one token, and its stored spelling is written after the
	 * cis - unless the writer spells it again (LilypondRelativizer). It came back an octave high.
	 */
	@Test
	@Timeout(300)
	public void aNoteSpelledAfterAnotherNoteComesBackAtItsPitch() throws IOException, InterruptedException {
		assumeTrue(Lymusic.available(), Lymusic.NEEDS);
		EccoService service = LilypondTraceMeasure.commitAfter(ROOT.resolve("lilypond_variants"), 2,
				EDITS.resolve("notes_leap"), "setup.1, notes.1, leap.1");
		Path checkoutDir = Files.createTempDirectory("lilypond-leap-checkout");
		service.setBaseDir(checkoutDir);
		service.checkout("setup.1, notes.1, leap.1");
		service.close();
		Lymusic.assertSameMusic(EDITS.resolve("notes_leap").resolve(FILE_NAME), checkoutDir.resolve(FILE_NAME),
				"notes,lyrics,articulations,slurs,dynamics,attributes", "leap");
	}

	private static EccoService commitEdit() throws IOException {
		return LilypondTraceMeasure.commitAfter(ROOT.resolve("lilypond_variants"), 3, EDITS.resolve("notes_edit"),
				"setup.1, notes.1, edit.1");
	}
}
