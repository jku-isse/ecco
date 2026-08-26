package at.jku.isse.ecco.gui.view.operation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ImportGitView#defaultConfigurationText}, {@link ImportGitView#applyConfigurationToRunning},
 * {@link ImportGitView#parseFeatureTokens} and {@link ImportGitView#isReviewCommit} are pure,
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

	@Test
	public void isReviewCommit_intervalOne_reviewsEveryCommit() {
		for (int index = 0; index < 5; index++) {
			assertTrue(ImportGitView.isReviewCommit(index, 1), "index " + index + " with interval 1");
		}
	}

	@Test
	public void isReviewCommit_intervalFive_reviewsEveryFifthCommitOnly() {
		// 0-based index -> 1-based commit position: reviews the 5th, 10th, 15th... commit
		assertFalse(ImportGitView.isReviewCommit(0, 5), "1st commit");
		assertFalse(ImportGitView.isReviewCommit(1, 5), "2nd commit");
		assertFalse(ImportGitView.isReviewCommit(2, 5), "3rd commit");
		assertFalse(ImportGitView.isReviewCommit(3, 5), "4th commit");
		assertTrue(ImportGitView.isReviewCommit(4, 5), "5th commit");
		assertFalse(ImportGitView.isReviewCommit(5, 5), "6th commit");
		assertTrue(ImportGitView.isReviewCommit(9, 5), "10th commit");
	}
}
