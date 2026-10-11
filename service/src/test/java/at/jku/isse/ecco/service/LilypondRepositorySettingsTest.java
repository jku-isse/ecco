package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A repository reads LilyPond the way it was created to (.ecco/.settings): musical tokens and variable
 * contexts in a new one, plain tokens and parce's contexts in one from before the settings, the
 * origin's choice in a local fork.
 */
public class LilypondRepositorySettingsTest {

	private static final Path V2 = LilypondVariantsCommitCheckoutTest.findRepoRoot()
			.resolve("examples").resolve("lilypond_variants").resolve("v2_setup_notes");

	private static Properties settings(Path repositoryDir) throws IOException {
		Properties properties = new Properties();
		try (Reader in = Files.newBufferedReader(repositoryDir.resolve(EccoService.SETTINGS_FILE_NAME))) {
			properties.load(in);
		}
		return properties;
	}

	@Test
	@Timeout(120)
	public void aNewRepositoryRecordsItsLilypondMode() throws IOException {
		Path repositoryDir = Files.createTempDirectory("lilypond-settings").resolve(".ecco");
		EccoService service = new EccoService();
		service.setRepositoryDir(repositoryDir);
		service.init();
		service.close();
		assertEquals(LilypondTraceMeasure.musicalTokens() ? "true" : "false",
				settings(repositoryDir).getProperty(LilypondPlugin.MUSICAL_TOKENS_SETTING));
		assertEquals(LilypondTraceMeasure.variableContexts() ? "true" : "false",
				settings(repositoryDir).getProperty(LilypondPlugin.VARIABLE_CONTEXTS_SETTING));
		assertEquals(System.getProperty("ecco.lilypond.parser", LilypondPlugin.LYBAR_PARSER),
				settings(repositoryDir).getProperty(LilypondPlugin.PARSER_SETTING));
	}

	@Test
	@Timeout(120)
	public void aRepositoryWithoutTheParserSettingIsReadWithParce() throws IOException {
		assumeTrue(Lymusic.available() && Lymusic.lybarAvailable(), "needs parce and lymodel's tree-sitter parse - " + Lymusic.NEEDS);
		// parce gives a dotted duration a context of its own (LilyPond.duration); lymodel's parse keeps
		// it in the note's token - so the context tells which of the two read the file
		EccoService parce = withParser(null);     // as before the setting existed
		assertTrue(LilypondTraceMeasure.hasContext(parce, "LilyPond.duration"), "read with parce");
		parce.close();

		EccoService lybar = withParser(LilypondPlugin.LYBAR_PARSER);
		assertFalse(LilypondTraceMeasure.hasContext(lybar, "LilyPond.duration"), "read with lymodel's tree-sitter parse");
		lybar.close();
	}

	/** A new repository whose settings say [parser] (none: null), with v2 committed. */
	private static EccoService withParser(String parser) throws IOException {
		Path repositoryDir = Files.createTempDirectory("lilypond-parser").resolve(".ecco");
		EccoService service = new EccoService();
		service.setRepositoryDir(repositoryDir);
		service.init();
		service.close();
		Properties settings = settings(repositoryDir);
		if (parser == null) {
			settings.remove(LilypondPlugin.PARSER_SETTING);
		} else {
			settings.setProperty(LilypondPlugin.PARSER_SETTING, parser);
		}
		try (java.io.Writer out = Files.newBufferedWriter(repositoryDir.resolve(EccoService.SETTINGS_FILE_NAME))) {
			settings.store(out, null);
		}
		service = new EccoService();
		service.setRepositoryDir(repositoryDir);
		service.open();
		service.setBaseDir(V2);
		service.commit("v2", "setup.1, notes.1");
		return service;
	}

	@Test
	@Timeout(120)
	public void aRepositoryWithoutSettingsHasNoVariableContexts() throws IOException {
		assumeTrue(LilypondTraceMeasure.readable(), LilypondTraceMeasure.UNREADABLE);
		Path repositoryDir = Files.createTempDirectory("lilypond-old-contexts").resolve(".ecco");
		EccoService service = new EccoService();
		service.setRepositoryDir(repositoryDir);
		service.init();
		service.close();
		Files.delete(repositoryDir.resolve(EccoService.SETTINGS_FILE_NAME));     // as before settings existed

		service = new EccoService();
		service.setRepositoryDir(repositoryDir);
		service.open();
		service.setBaseDir(V2);
		service.commit("v2", "setup.1, notes.1");
		assertTrue(LilypondTraceMeasure.variableContexts(service).isEmpty(), "its artifacts have parce's contexts only");
		service.close();
	}

	@Test
	@Timeout(120)
	public void aRepositoryWithoutSettingsKeepsPlainTokens() throws IOException {
		assumeTrue(LilypondTraceMeasure.readable(), LilypondTraceMeasure.UNREADABLE);
		Path repositoryDir = Files.createTempDirectory("lilypond-old").resolve(".ecco");
		EccoService service = new EccoService();
		service.setRepositoryDir(repositoryDir);
		service.init();
		service.close();
		Files.delete(repositoryDir.resolve(EccoService.SETTINGS_FILE_NAME));     // as before settings existed

		service = new EccoService();
		service.setRepositoryDir(repositoryDir);
		service.open();
		service.setBaseDir(V2);
		service.commit("v2", "setup.1, notes.1");
		int traced = LilypondTraceMeasure.tokensTracedTo(service, "notes").size();
		service.close();

		Path musicalDir = Files.createTempDirectory("lilypond-new").resolve(".ecco");
		EccoService musical = new EccoService();
		musical.setRepositoryDir(musicalDir);
		musical.init();
		musical.setBaseDir(V2);
		musical.commit("v2", "setup.1, notes.1");
		int tracedMusical = LilypondTraceMeasure.tokensTracedTo(musical, "notes").size();
		musical.close();
		// plain tokens: a note's pitch, octave marks and duration are tokens of their own
		if (LilypondTraceMeasure.musicalTokens()) {
			assertTrue(traced > tracedMusical, traced + " plain tokens, " + tracedMusical + " musical");
		} else {
			assertEquals(traced, tracedMusical);
		}
	}

	@Test
	@Timeout(120)
	public void aLocalForkTakesItsOriginsSettings() throws IOException {
		Path origin = Files.createTempDirectory("lilypond-origin").resolve(".ecco");
		EccoService service = new EccoService();
		service.setRepositoryDir(origin);
		service.init();
		service.close();
		Files.delete(origin.resolve(EccoService.SETTINGS_FILE_NAME));

		Path forkDir = Files.createTempDirectory("lilypond-fork").resolve(".ecco");
		EccoService fork = new EccoService();
		fork.setRepositoryDir(forkDir);
		fork.fork(origin);
		fork.close();
		assertFalse(Files.exists(forkDir.resolve(EccoService.SETTINGS_FILE_NAME)), "an old origin's fork reads with plain tokens too");
	}
}
