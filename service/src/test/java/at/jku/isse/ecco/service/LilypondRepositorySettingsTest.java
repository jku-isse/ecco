package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A repository reads LilyPond the way it was created to (.ecco/.settings): lymodel's tree-sitter parse
 * with musical tokens and variable contexts in a new one, plain tokens or no variable contexts when its
 * settings say so, the origin's choice in a local fork - and one from before the parser setting, read
 * with parce, checks out but takes no more commits.
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

	/** Sets [key] to [value] in the repository's settings, or removes it when [value] is null. */
	private static void setting(Path repositoryDir, String key, String value) throws IOException {
		Properties settings = settings(repositoryDir);
		if (value == null) {
			settings.remove(key);
		} else {
			settings.setProperty(key, value);
		}
		try (Writer out = Files.newBufferedWriter(repositoryDir.resolve(EccoService.SETTINGS_FILE_NAME))) {
			settings.store(out, null);
		}
	}

	private static Path newRepository(String name) throws IOException {
		Path repositoryDir = Files.createTempDirectory(name).resolve(".ecco");
		EccoService service = new EccoService();
		service.setRepositoryDir(repositoryDir);
		service.init();
		service.close();
		return repositoryDir;
	}

	/** The repository opened, with v2 committed. */
	private static EccoService withV2(Path repositoryDir) {
		EccoService service = new EccoService();
		service.setRepositoryDir(repositoryDir);
		service.open();
		service.setBaseDir(V2);
		service.commit("v2", "setup.1, notes.1");
		return service;
	}

	@Test
	@Timeout(120)
	public void aNewRepositoryRecordsItsLilypondMode() throws IOException {
		Path repositoryDir = newRepository("lilypond-settings");
		assertEquals(LilypondTraceMeasure.musicalTokens() ? "true" : "false",
				settings(repositoryDir).getProperty(LilypondPlugin.MUSICAL_TOKENS_SETTING));
		assertEquals(LilypondTraceMeasure.variableContexts() ? "true" : "false",
				settings(repositoryDir).getProperty(LilypondPlugin.VARIABLE_CONTEXTS_SETTING));
		assertEquals(LilypondPlugin.LYBAR_PARSER, settings(repositoryDir).getProperty(LilypondPlugin.PARSER_SETTING));
	}

	@Test
	@Timeout(120)
	public void aRepositoryReadWithParceChecksOutButTakesNoCommits() throws IOException {
		assumeTrue(LilypondTraceMeasure.readable(), LilypondTraceMeasure.UNREADABLE);
		Path repositoryDir = newRepository("lilypond-parce");
		withV2(repositoryDir).close();
		setting(repositoryDir, LilypondPlugin.PARSER_SETTING, null);     // as before the setting existed

		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(repositoryDir);
			service.open();
			Path checkout = Files.createTempDirectory("lilypond-parce-checkout");
			service.setBaseDir(checkout);
			service.checkout("setup.1, notes.1");
			assertTrue(Files.size(checkout.resolve("chanson.ly")) > 0, "it checks out");

			service.setBaseDir(V2);
			EccoException refused = assertThrows(EccoException.class, () -> service.commit("again", "setup.1, notes.2"));
			assertTrue(String.valueOf(refused.getMessage()).contains("parce")
					|| refused.getCause() != null && String.valueOf(refused.getCause().getMessage()).contains("parce"),
					String.valueOf(refused));
		}
	}

	@Test
	@Timeout(120)
	public void aRepositoryWithoutVariableContextsHasNone() throws IOException {
		assumeTrue(LilypondTraceMeasure.readable(), LilypondTraceMeasure.UNREADABLE);
		Path repositoryDir = newRepository("lilypond-no-contexts");
		setting(repositoryDir, LilypondPlugin.VARIABLE_CONTEXTS_SETTING, "false");
		EccoService service = withV2(repositoryDir);
		assertTrue(LilypondTraceMeasure.variableContexts(service).isEmpty(), "no variable contexts");
		service.close();
	}

	@Test
	@Timeout(120)
	public void aRepositoryWithoutMusicalTokensKeepsPlainTokens() throws IOException {
		assumeTrue(LilypondTraceMeasure.readable(), LilypondTraceMeasure.UNREADABLE);
		Path plainDir = newRepository("lilypond-plain");
		setting(plainDir, LilypondPlugin.MUSICAL_TOKENS_SETTING, "false");
		EccoService plain = withV2(plainDir);
		int traced = LilypondTraceMeasure.tokensTracedTo(plain, "notes").size();
		plain.close();

		Path musicalDir = newRepository("lilypond-musical");
		setting(musicalDir, LilypondPlugin.MUSICAL_TOKENS_SETTING, "true");
		EccoService musical = withV2(musicalDir);
		int tracedMusical = LilypondTraceMeasure.tokensTracedTo(musical, "notes").size();
		musical.close();
		// plain tokens: a note's pitch, octave marks and duration are tokens of their own
		assertTrue(traced > tracedMusical, traced + " plain tokens, " + tracedMusical + " musical");
	}

	@Test
	@Timeout(120)
	public void aLocalForkTakesItsOriginsSettings() throws IOException {
		Path origin = newRepository("lilypond-origin");
		Files.delete(origin.resolve(EccoService.SETTINGS_FILE_NAME));

		Path forkDir = Files.createTempDirectory("lilypond-fork").resolve(".ecco");
		EccoService fork = new EccoService();
		fork.setRepositoryDir(forkDir);
		fork.fork(origin);
		fork.close();
		assertFalse(Files.exists(forkDir.resolve(EccoService.SETTINGS_FILE_NAME)), "an old origin's fork is an old repository too");
	}
}
