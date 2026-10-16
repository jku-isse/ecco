package at.jku.isse.ecco.service.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ImportedFeatureFilesTest {

	@Test
	public void labelsAreTheAddedFeaturesAndTheKeptSuggestions() {
		// a new feature, suggested and kept
		assertEquals(Set.of("B"), ImportedFeatureFiles.labels("A", "B", "A, B"));
		// a known feature changed again: the configuration does not change, the kept suggestion says which
		assertEquals(Set.of("A"), ImportedFeatureFiles.labels("A, B", "A", "A, B"));
		// a suggestion the reviewer replaced
		assertEquals(Set.of("C"), ImportedFeatureFiles.labels("A", "B", "A, C"));
		// a removal is no label; nothing added and no suggestion: no label
		assertEquals(Set.of(), ImportedFeatureFiles.labels("A, B", "-B", "A"));
		assertEquals(Set.of(), ImportedFeatureFiles.labels("A, B", null, "A, B"));
		// the first commit
		assertEquals(Set.of("A", "B"), ImportedFeatureFiles.labels("", null, "A, B"));
	}

	@Test
	public void filesAccumulatePerLabel() {
		ImportedFeatureFiles files = new ImportedFeatureFiles();
		files.add(List.of("A"), List.of("a.c", "s.h"));
		files.add(List.of("A", "B"), List.of("b.c"));

		assertEquals(Map.of("A", Set.of("a.c", "b.c", "s.h"), "B", Set.of("b.c")), files.files());
	}
}
