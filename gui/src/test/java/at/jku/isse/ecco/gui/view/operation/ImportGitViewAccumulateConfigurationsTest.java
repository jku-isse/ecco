package at.jku.isse.ecco.gui.view.operation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ImportGitView#accumulateConfigurations} is a pure, JavaFX-free function specifically so
 * it can be exercised like this.
 */
public class ImportGitViewAccumulateConfigurationsTest {

	@Test
	public void laterCommitCarriesForwardEarlierFeatures() {
		// the exact real-world regression this guards against: v1 introduces "A", v2 only adds "B"
		// but the resulting configuration must still include "A" too, not just "B"
		List<String> accumulated = ImportGitView.accumulateConfigurations(List.of("A", "B"));

		assertEquals(List.of("A", "A, B"), accumulated);
	}

	@Test
	public void duplicateFeatureAcrossCommitsIsNotRepeated() {
		List<String> accumulated = ImportGitView.accumulateConfigurations(List.of("A", "A, B", "A"));

		assertEquals(List.of("A", "A, B", "A, B"), accumulated);
	}

	@Test
	public void blankSuggestionCarriesPreviousConfigurationForwardUnchanged() {
		// a commit the LLM couldn't classify (or suggestions were skipped) shouldn't look like it
		// dropped every previously-active feature
		List<String> accumulated = ImportGitView.accumulateConfigurations(List.of("A", "", "B"));

		assertEquals(List.of("A", "A", "A, B"), accumulated);
	}

	@Test
	public void allBlankStaysBlank() {
		// the non-LLM ("Suggest features" unchecked) path passes all-blank suggestions through here
		// unconditionally - must remain a no-op
		List<String> accumulated = ImportGitView.accumulateConfigurations(List.of("", "", ""));

		assertEquals(List.of("", "", ""), accumulated);
	}

	@Test
	public void whitespaceAroundFeatureNamesIsTrimmed() {
		List<String> accumulated = ImportGitView.accumulateConfigurations(List.of(" A ,B", "C"));

		assertEquals(List.of("A, B", "A, B, C"), accumulated);
	}

	@Test
	public void emptyInputYieldsEmptyOutput() {
		assertEquals(List.of(), ImportGitView.accumulateConfigurations(List.of()));
	}
}
