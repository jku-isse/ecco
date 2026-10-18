package at.jku.isse.ecco.gui.view.operation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ImportGitView#defaultConfigurationText}, {@link ImportGitView#applyConfigurationToRunning},
 * {@link ImportGitView#parseFeatureTokens} is pure,
 * JavaFX-free functions specifically so they can be exercised like this - they drive the per-commit
 * interactive import loop's "what should this commit's configuration default to" and "should this
 * commit pause for review or auto-import" logic.
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

		// named again: a new revision of it, not a second A
		assertEquals("A', B", ImportGitView.defaultConfigurationText(running, "A"));
	}

	/**
	 * A feature the suggestion names that is known already - imported before in this run or in the
	 * repository - gets a new revision ("A'"): the commit changes it. With one revision per feature
	 * and nearly the same features on every commit, code that came or changed later could be traced
	 * to no feature (tinycc's import: 95% of its associations; zip4j's first 275 commits: 28%, 1.3%
	 * with revisions). A new feature is its first revision; one not named stays as it is.
	 */
	@Test
	public void aKnownFeatureTheSuggestionNamesGetsANewRevision() {
		LinkedHashSet<String> running = new LinkedHashSet<>(java.util.List.of("BASE", "A", "B"));
		assertEquals("BASE, A', B, C", ImportGitView.defaultConfigurationText(running, java.util.Set.of(), "A, C"));
		// known from the repository though not running: named, a new revision
		assertEquals("BASE, A, B, D'", ImportGitView.defaultConfigurationText(running, java.util.Set.of("D"), "D"));
		// a removal stays one
		assertEquals("BASE, B", ImportGitView.defaultConfigurationText(running, java.util.Set.of(), "-A"));
		// the generic features keep one revision: zip4j's tags revised "misc" in 37 of 40 commits
		LinkedHashSet<String> withGeneric = new LinkedHashSet<>(java.util.List.of("BASE", "A", "misc", "documentation"));
		assertEquals("BASE, A', misc, documentation", ImportGitView.defaultConfigurationText(withGeneric, java.util.Set.of(), "A, misc, documentation"));
		// nothing suggested: nothing revised
		assertEquals("BASE, A, B", ImportGitView.defaultConfigurationText(running, java.util.Set.of(), ""));

		// the running features are the plain names
		ImportGitView.applyConfigurationToRunning(running, "BASE, A', B, C");
		assertEquals(java.util.List.of("BASE", "A", "B", "C"), java.util.List.copyOf(running));
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

	@Test
	public void suggestionCanRemoveAPreviouslyImportedFeature() {
		// the exact real-world case this exists for: a later commit deletes an earlier feature's
		// own implementation, and the LLM signals that with a "-name" token (see
		// LlmFeatureSuggestionClient's SYSTEM_PROMPT) instead of silently carrying it forward forever
		LinkedHashSet<String> running = new LinkedHashSet<>();
		ImportGitView.applyConfigurationToRunning(running, "A, B");

		String afterRemoval = ImportGitView.defaultConfigurationText(running, "-A");
		assertEquals("B", afterRemoval);
	}

	@Test
	public void suggestionCanAddAndRemoveInTheSameCommit() {
		LinkedHashSet<String> running = new LinkedHashSet<>();
		ImportGitView.applyConfigurationToRunning(running, "A, B");

		assertEquals("B, C", ImportGitView.defaultConfigurationText(running, "C, -A"));
	}

	@Test
	public void removingAFeatureNotCurrentlyRunningIsANoOp() {
		LinkedHashSet<String> running = new LinkedHashSet<>();
		ImportGitView.applyConfigurationToRunning(running, "A");

		assertEquals("A", ImportGitView.defaultConfigurationText(running, "-Z"));
	}

	@Test
	public void aLoneMinusSignIsTreatedAsALiteralFeatureNameNotARemoval() {
		// "-" alone has nothing after the minus to remove - defaultConfigurationText requires at
		// least one character after "-" before treating a token as a removal (see its trimmed.length() > 1
		// guard), so this edge case can't silently no-op into losing the token entirely
		LinkedHashSet<String> running = new LinkedHashSet<>();
		assertEquals("-", ImportGitView.defaultConfigurationText(running, "-"));
	}

}
