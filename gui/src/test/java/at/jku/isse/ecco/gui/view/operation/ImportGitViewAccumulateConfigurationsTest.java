package at.jku.isse.ecco.gui.view.operation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ImportGitView#defaultConfigurationText}, {@link ImportGitView#applyConfigurationToRunning}
 * and {@link ImportGitView#parseFeatureTokens} are pure, JavaFX-free functions specifically so they
 * can be exercised like this - they drive the per-commit interactive import loop's "what should
 * this commit's configuration default to" logic.
 */
public class ImportGitViewAccumulateConfigurationsTest {

	@Test
	public void laterCommitCarriesForwardEarlierFeatures() {
		// the exact real-world regression this guards against: v1 introduces "A", v2 only adds "B"
		// but the resulting default configuration must still include "A" too, not just "B"
		LinkedHashSet<String> running = new LinkedHashSet<>();
		String firstDefault = ImportGitView.defaultConfigurationText(running, "A");
		assertEquals("A", firstDefault);

		ImportGitView.applyConfigurationToRunning(running, firstDefault);
		String secondDefault = ImportGitView.defaultConfigurationText(running, "B");
		assertEquals("A, B", secondDefault);
	}

	@Test
	public void duplicateFeatureAcrossCommitsIsNotRepeated() {
		LinkedHashSet<String> running = new LinkedHashSet<>();
		ImportGitView.applyConfigurationToRunning(running, "A, B");

		assertEquals("A, B", ImportGitView.defaultConfigurationText(running, "A"));
	}

	@Test
	public void blankSuggestionCarriesRunningConfigurationForwardUnchanged() {
		// a commit the LLM couldn't classify (or suggestions were skipped) shouldn't look like it
		// dropped every previously-imported feature
		LinkedHashSet<String> running = new LinkedHashSet<>();
		ImportGitView.applyConfigurationToRunning(running, "A");

		assertEquals("A", ImportGitView.defaultConfigurationText(running, ""));
		assertEquals("A", ImportGitView.defaultConfigurationText(running, null));
	}

	@Test
	public void skippedCommitDoesNotAffectLaterDefaults() {
		// applyConfigurationToRunning is only ever called for an actually-imported commit - a
		// skipped commit's suggestion must not leak into the next commit's default
		LinkedHashSet<String> running = new LinkedHashSet<>();
		ImportGitView.applyConfigurationToRunning(running, "A");

		// commit 2 is shown "A, B" as a default but the user Skips it - running is untouched
		String skippedDefault = ImportGitView.defaultConfigurationText(running, "B");
		assertEquals("A, B", skippedDefault);

		// commit 3's default must build on "A" only, not "A, B"
		assertEquals("A", ImportGitView.defaultConfigurationText(running, ""));
	}

	@Test
	public void allBlankStaysBlank() {
		// the non-LLM ("Suggest features" unchecked) path with nothing imported yet must remain a no-op
		assertEquals("", ImportGitView.defaultConfigurationText(new LinkedHashSet<>(), ""));
	}

	@Test
	public void whitespaceAroundFeatureNamesIsTrimmed() {
		LinkedHashSet<String> running = new LinkedHashSet<>();
		String firstDefault = ImportGitView.defaultConfigurationText(running, " A ,B");
		assertEquals("A, B", firstDefault);

		ImportGitView.applyConfigurationToRunning(running, firstDefault);
		assertEquals("A, B, C", ImportGitView.defaultConfigurationText(running, "C"));
	}

	@Test
	public void parseFeatureTokensHandlesNullAndBlank() {
		assertEquals(new LinkedHashSet<>(), ImportGitView.parseFeatureTokens(null));
		assertEquals(new LinkedHashSet<>(), ImportGitView.parseFeatureTokens(""));
		assertEquals(new LinkedHashSet<>(java.util.List.of("A", "B")), ImportGitView.parseFeatureTokens(" A , B ,"));
	}
}
