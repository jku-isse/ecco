package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A version that moves a voice's definition (examples/lilypond_edits/notes_moved: the alto's before the
 * soprano's). parce gives every voice's notes the same context, LilyPond.musiclist, so ECCO matched
 * the voices by their order - it stored `AltoVoice =` with soprano notes - and a feature committed
 * before the move put its accents on other notes. With variable contexts each voice is the context
 * of its name: the move is the soprano taken out and put back, and the accent committed on the
 * soprano at its old place is the only thing missing.
 */
public class LilypondMovedVoicesTest {

	private static final Path ROOT = LilypondVariantsCommitCheckoutTest.findRepoRoot().resolve("examples");
	private static final Path EDITS = ROOT.resolve("lilypond_edits");
	private static final String FILE_NAME = "dieu.ly";

	private static EccoService commitMove() throws IOException {
		return LilypondTraceMeasure.commitAfter(ROOT.resolve("lilypond_variants"), 3, EDITS.resolve("notes_moved"),
				"setup.1, notes.1, order.1");
	}

	@Test
	@Timeout(300)
	public void eachVoiceIsAContextOfItsName() throws IOException {
		assumeTrue(LilypondTraceMeasure.variableContexts() && Lymusic.available(), "needs variable contexts (the default) and lymodel");
		EccoService service = commitMove();
		Set<String> names = LilypondTraceMeasure.variableContexts(service);
		service.close();
		assertEquals(Set.of("AltoVoice", "BassVoice", "SopranoVoice", "TenorVoice", "global"), names);
	}

	/** The moved soprano at its new place (36 tokens) and at its old one (37); parce's contexts traced 129. */
	@Test
	@Timeout(300)
	public void aMovedVoiceIsTracedAsThatVoiceOnly() throws IOException {
		assumeTrue(LilypondTraceMeasure.variableContexts() && LilypondTraceMeasure.musicalTokens() && Lymusic.available(),
				"needs variable contexts and musical tokens (the defaults) and lymodel");
		EccoService service = commitMove();
		List<String> traced = LilypondTraceMeasure.tokensTracedTo(service, "order");
		service.close();
		assertEquals(73, traced.size(), String.join("\n", traced));
	}

	@Test
	@Timeout(300)
	public void aFeatureCommittedBeforeTheMoveKeepsItsAccentsOnTheirNotes() throws IOException, InterruptedException {
		assumeTrue(LilypondTraceMeasure.variableContexts() && LilypondTraceMeasure.musicalTokens() && Lymusic.available(),
				"needs variable contexts and musical tokens (the defaults) and lymodel");
		EccoService service = commitMove();
		Path checkoutDir = Files.createTempDirectory("lilypond-moved-checkout");
		service.setBaseDir(checkoutDir);
		service.checkout("setup.1, notes.1, articulation.1, order.1");
		service.close();
		Lymusic.assertSameMusic(EDITS.resolve("notes_articulation_moved").resolve(FILE_NAME), checkoutDir.resolve(FILE_NAME),
				"notes,lyrics,articulations,slurs,dynamics,attributes", "articulation + moved voices");
	}
}
