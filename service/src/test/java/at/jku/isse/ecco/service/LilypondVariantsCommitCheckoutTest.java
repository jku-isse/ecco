package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Commits the LilyPond variant evolution history under examples/lilypond_variants (see its README)
 * and checks out all six feature combinations, verifying the checked-out dieu.ly matches the
 * original exactly. Exercises the lilypond adapter through EccoService's real commit/checkout flow,
 * unlike adapter/lilypond's own test suite, which drives the adapter's reader/writer/transformer
 * directly rather than the full repository round trip. Mirrors CVariantsCommitCheckoutTest.
 */
public class LilypondVariantsCommitCheckoutTest {

	private static final Path EXAMPLES_DIR = findRepoRoot().resolve("examples").resolve("lilypond_variants");
	private static final String FILE_NAME = "dieu.ly";

	private static final List<String> VARIANT_DIRS = List.of(
			"v1_setup",
			"v2_setup_notes",
			"v3_setup_notes_articulation",
			"v4_setup_notes_articulation_lyrics",
			"v5_setup_notes_articulation_lyrics_slurs",
			"v6_setup_notes_articulation_lyrics_dynamics"
	);

	private static final List<String> CONFIGURATIONS = List.of(
			"setup.1",
			"setup.1, notes.1",
			"setup.1, notes.1, articulation.1",
			"setup.1, notes.1, articulation.1, lyrics.1",
			"setup.1, notes.1, articulation.1, lyrics.1, slurs.1",
			"setup.1, notes.1, articulation.1, lyrics.1, slurs.1, dynamics.1"
	);

	@Test
	@Timeout(60)
	public void commitAllVariants_thenCheckoutEach_reproducesOriginalContent() throws IOException {
		EccoService service = commitAllVariants();

		for (int i = 0; i < VARIANT_DIRS.size(); i++) {
			checkoutAndVerify(service, CONFIGURATIONS.get(i), VARIANT_DIRS.get(i));
		}

		service.close();
	}

	/**
	 * Feature combinations that were never committed as a whole variant (so there's no reference
	 * .ly file to compare against byte-for-byte), but that a real user could still request via
	 * Checkout - unlike the six committed variants (each strictly adds one more feature on top of
	 * the last), these skip an in-between feature, exercising ecco's compose logic rather than just
	 * replaying committed history. Confirmed correct by manually inspecting each checkout's full
	 * content before writing these assertions (not just "didn't throw", and not just the first part
	 * of the file - see the class javadoc's note on slurs.1 for why that matters).
	 * <p>
	 * notes.1 is a hard prerequisite for every annotation feature (articulation/slurs/dynamics mark
	 * up specific notes; lyrics is sung against them via \lyricsto) - checking out articulation.1 or
	 * lyrics.1 WITHOUT notes.1 does not throw either, but silently produces corrupted output (e.g.
	 * an articulation mark stranded on a \clef token with no note to attach to) - deliberately not
	 * asserted as "working" here. slurs.1 is deliberately NOT included below either: combined with
	 * anything except lyrics.1, it leaks a stray, disconnected \lyricmode block into the checkout
	 * even though lyrics.1 wasn't requested - most likely because slurs was only ever committed (v5)
	 * alongside lyrics. Not a correctness bug (the output is still valid, renderable LilyPond, and
	 * the stray block is inert since it's not wired via \lyricsto), just dead content - but it
	 * doesn't match this test's "exactly the requested features, nothing else" bar, so it's left out.
	 */
	@Test
	@Timeout(60)
	public void checkoutNovelCombinations_composesIndependentFeaturesCorrectly() throws IOException {
		EccoService service = commitAllVariants();

		// lyrics is a structurally independent addition (new \header/\tempo/\lyricmode blocks, not
		// markup on existing notes) - should compose without articulation/slurs/dynamics
		String lyricsOnly = checkout(service, "setup.1, notes.1, lyrics.1");
		assertTrue(lyricsOnly.contains("\\lyricmode"), "expected lyrics content");
		assertTrue(lyricsOnly.contains("Dieu! qu'il la fait bon regarder"), "expected lyrics text");
		assertFalse(lyricsOnly.contains("^-"), "articulation should be absent");
		assertFalse(lyricsOnly.contains("\\mf"), "dynamics should be absent");

		// dynamics alone (no articulation/slurs/lyrics)
		String dynamicsOnly = checkout(service, "setup.1, notes.1, dynamics.1");
		assertTrue(dynamicsOnly.contains("\\mf"), "expected dynamics markup");
		assertFalse(dynamicsOnly.contains("^-"), "articulation should be absent");
		assertFalse(dynamicsOnly.contains("\\("), "slurs should be absent");
		assertFalse(dynamicsOnly.contains("\\lyricmode"), "lyrics should be absent");

		// two orthogonal annotation features together, without lyrics/slurs - both mark up the same
		// notes (e.g. "fis2^-\mf"), so this specifically checks they combine rather than clobber
		String articulationAndDynamics = checkout(service, "setup.1, notes.1, articulation.1, dynamics.1");
		assertTrue(articulationAndDynamics.contains("^-"), "expected articulation");
		assertTrue(articulationAndDynamics.contains("\\mf"), "expected dynamics");
		assertFalse(articulationAndDynamics.contains("\\("), "slurs should be absent");
		assertFalse(articulationAndDynamics.contains("\\lyricmode"), "lyrics should be absent");

		service.close();
	}

	/**
	 * The same round trip, judged by the MUSIC each checkout holds rather than its tokens: notes
	 * (absolute pitch, start, length, ties), lyrics (each syllable and where it is sung),
	 * articulations, slurs, dynamics, and clefs/keys/meters - compared by lymusic from the
	 * lymodel (the lilypond-idea-plugin's python/, verify/lymusic.py), which reads a .ly file the way its MusicXML
	 * export does. Unlike the whitespace-stripped text comparison above, it does not care how the
	 * music is spelled (\relative octave marks, implicit durations, line breaks), only whether it
	 * is the same music. Taken from LYPYTHON (the plugin's python/ directory, which
	 * silverswan.sh exports) or an installed lymodel; skipped without either (see Lymusic).
	 */
	@Test
	@Timeout(300)
	public void commitAllVariants_thenCheckoutEach_holdsTheOriginalsMusic() throws IOException, InterruptedException {
		assumeTrue(Lymusic.available(), Lymusic.NEEDS);
		EccoService service = commitAllVariants();

		for (int i = 0; i < VARIANT_DIRS.size(); i++) {
			Path checkedOut = checkoutFile(service, CONFIGURATIONS.get(i));
			Lymusic.assertSameMusic(EXAMPLES_DIR.resolve(VARIANT_DIRS.get(i)).resolve(FILE_NAME), checkedOut, ALL_ASPECTS,
					CONFIGURATIONS.get(i));
		}

		service.close();
	}

	/**
	 * The never-committed feature combinations, judged by their music: each requested feature must be
	 * exactly as in the variant that introduced it, and each one left out exactly as in v2 (notes
	 * only) - which also says the music is otherwise untouched. Stronger than the substring checks in
	 * checkoutNovelCombinations_composesIndependentFeaturesCorrectly, and it includes slurs.1, which
	 * that test leaves out because of the stray \lyricmode block it leaks: a block no \lyricsto
	 * binds, so it adds no lyrics to the music - musically, the checkout is right.
	 */
	@Test
	@Timeout(300)
	public void checkoutNovelCombinations_holdExactlyTheRequestedFeaturesMusic() throws IOException, InterruptedException {
		assumeTrue(Lymusic.available(), Lymusic.NEEDS);
		EccoService service = commitAllVariants();
		Path notesOnly = variant("v2_setup_notes");
		Path all = variant("v6_setup_notes_articulation_lyrics_dynamics");

		Path lyrics = checkoutFile(service, "setup.1, notes.1, lyrics.1");
		Lymusic.assertSameMusic(variant("v4_setup_notes_articulation_lyrics"), lyrics, "notes,lyrics,attributes", "lyrics");
		Lymusic.assertSameMusic(notesOnly, lyrics, "articulations,slurs,dynamics", "lyrics");

		Path dynamics = checkoutFile(service, "setup.1, notes.1, dynamics.1");
		Lymusic.assertSameMusic(all, dynamics, "notes,dynamics,attributes", "dynamics");
		Lymusic.assertSameMusic(notesOnly, dynamics, "articulations,slurs,lyrics", "dynamics");

		Path articulationAndDynamics = checkoutFile(service, "setup.1, notes.1, articulation.1, dynamics.1");
		Lymusic.assertSameMusic(all, articulationAndDynamics, "notes,articulations,dynamics,attributes", "articulation+dynamics");
		Lymusic.assertSameMusic(notesOnly, articulationAndDynamics, "slurs,lyrics", "articulation+dynamics");

		Path slurs = checkoutFile(service, "setup.1, notes.1, slurs.1");
		Lymusic.assertSameMusic(variant("v5_setup_notes_articulation_lyrics_slurs"), slurs, "notes,slurs,attributes", "slurs");
		Lymusic.assertSameMusic(notesOnly, slurs, "articulations,lyrics,dynamics", "slurs");

		service.close();
	}

	private static final String ALL_ASPECTS = "notes,lyrics,articulations,slurs,dynamics,attributes";

	private static Path variant(String dirName) {
		return EXAMPLES_DIR.resolve(dirName).resolve(FILE_NAME);
	}

	private Path checkoutFile(EccoService service, String configurationString) throws IOException {
		Path checkoutDir = Files.createTempDirectory("lilypond-variants-checkout");
		service.setBaseDir(checkoutDir);
		service.checkout(configurationString);
		return checkoutDir.resolve(FILE_NAME);
	}

	private EccoService commitAllVariants() throws IOException {
		EccoService service = new EccoService();
		service.setRepositoryDir(Files.createTempDirectory("lilypond-variants-repo").resolve(".ecco"));
		service.init();

		for (int i = 0; i < VARIANT_DIRS.size(); i++) {
			commit(service, VARIANT_DIRS.get(i), CONFIGURATIONS.get(i));
		}
		return service;
	}

	private String checkout(EccoService service, String configurationString) throws IOException {
		Path checkoutDir = Files.createTempDirectory("lilypond-variants-checkout");
		service.setBaseDir(checkoutDir);
		service.checkout(configurationString);
		return Files.readString(checkoutDir.resolve(FILE_NAME), StandardCharsets.UTF_8);
	}

	private void commit(EccoService service, String variantDirName, String configurationString) {
		service.setBaseDir(EXAMPLES_DIR.resolve(variantDirName));
		service.commit(variantDirName, configurationString);
	}

	private void checkoutAndVerify(EccoService service, String configurationString, String expectedVariantDirName) throws IOException {
		Path checkoutDir = Files.createTempDirectory("lilypond-variants-checkout");
		service.setBaseDir(checkoutDir);
		service.checkout(configurationString);

		String expectedContent = normalizeWhitespace(Files.readString(EXAMPLES_DIR.resolve(expectedVariantDirName).resolve(FILE_NAME), StandardCharsets.UTF_8));
		String actualContent = normalizeWhitespace(Files.readString(checkoutDir.resolve(FILE_NAME), StandardCharsets.UTF_8));
		assertEquals(expectedContent, actualContent, () -> FILE_NAME + " mismatch for configuration \"" + configurationString + "\"");
	}

	/**
	 * The lilypond writer doesn't preserve original whitespace at all - it freely adds/removes
	 * spaces around punctuation like "=", "{", "}", "(", ")" (e.g. "tagline =\"\"" round-trips as
	 * "tagline = \"\"", "#(set-default-paper-size ...)" as "#( set-default-paper-size ... )"),
	 * confirmed by diffing real checkouts against their source: collapsing whitespace runs still
	 * left punctuation-adjacent spacing mismatches, so this strips whitespace entirely rather than
	 * trying to match the writer's specific spacing rules. Same class of adapter quirk as
	 * CVariantsCommitCheckoutTest's withoutBlankLines - compare at the level of fidelity the
	 * adapter actually guarantees (same tokens), not byte-for-byte formatting.
	 */
	private String normalizeWhitespace(String content) {
		return content.replaceAll("\\s+", "");
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
