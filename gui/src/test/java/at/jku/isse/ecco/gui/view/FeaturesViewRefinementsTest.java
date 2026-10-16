package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.service.llm.ImportedFeatureTraces;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FeaturesViewRefinementsTest {

	@Test
	public void suggestedRefinementsInWords() {
		assertEquals("", FeaturesView.refinementsText(List.of()));
		assertEquals("Suggested refinements: store-compression probably refines add-files-to-zip (26% of its changes in code add-files-to-zip created); "
						+ "zip-slip-fix probably refines extract-files (47% of its changes in code extract-files created)",
				FeaturesView.refinementsText(List.of(
						new ImportedFeatureTraces.ParentSuggestion("store-compression", "add-files-to-zip", 0.26, 5.4),
						new ImportedFeatureTraces.ParentSuggestion("zip-slip-fix", "extract-files", 0.47, 24.1))));
	}
}
