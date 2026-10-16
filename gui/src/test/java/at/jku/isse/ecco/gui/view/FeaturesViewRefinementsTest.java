package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.service.llm.ImportedFeatureTraces;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FeaturesViewRefinementsTest {

	@Test
	public void suggestedSubfeaturesAndRequiresInWords() {
		assertEquals("", FeaturesView.refinementsText(List.of()));
		assertEquals("Suggested subfeatures: zip-slip-fix of extract-files (47% of its changes in code extract-files created). "
						+ "Suggested REQUIRES: aes requires store (26% of its changes in code store created; 1 source file of its own).",
				FeaturesView.refinementsText(List.of(
						new ImportedFeatureTraces.RelationSuggestion("zip-slip-fix", "extract-files", ImportedFeatureTraces.Relation.SUBFEATURE, 0.47, 24.1, 0),
						new ImportedFeatureTraces.RelationSuggestion("aes", "store", ImportedFeatureTraces.Relation.REQUIRES, 0.26, 5.4, 1))));
	}
}
