package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.service.llm.ImportedFeatureTraces;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A mined REQUIRES the code backs up says so, and how: as a subfeature, or a cross-tree REQUIRES. */
public class ConstraintSuggestionsCodeEvidenceTest {

	@Test
	public void requiresBackedByTheCode() {
		Map<String, ImportedFeatureTraces.RelationSuggestion> relations = Map.of(
				"zip-slip-fix\0extract-files", new ImportedFeatureTraces.RelationSuggestion("zip-slip-fix", "extract-files", ImportedFeatureTraces.Relation.SUBFEATURE, 0.47, 24, 0),
				"aes\0store", new ImportedFeatureTraces.RelationSuggestion("aes", "store", ImportedFeatureTraces.Relation.REQUIRES, 0.26, 6, 1));
		List<ConstraintMiner.Suggestion> mined = new ConstraintMiner(1, 1.0, null).mine(List.of(
				Set.of("base", "extract-files", "zip-slip-fix"), Set.of("base", "extract-files"),
				Set.of("base", "store", "aes"), Set.of("base", "store")));

		ConstraintMiner.Suggestion slip = find(mined, "zip-slip-fix", "extract-files");
		ConstraintMiner.Suggestion aes = find(mined, "aes", "store");
		assertEquals("subfeature (47%)", ConstraintSuggestionsView.codeEvidence(relations, slip));
		assertEquals("requires (26%, 1 own file)", ConstraintSuggestionsView.codeEvidence(relations, aes));
		ConstraintMiner.Suggestion excludes = mined.stream().filter(s -> s.kind == Constraint.Kind.EXCLUDES).findFirst().orElseThrow();
		assertEquals("", ConstraintSuggestionsView.codeEvidence(relations, excludes), "only REQUIRES");
	}

	private static ConstraintMiner.Suggestion find(List<ConstraintMiner.Suggestion> mined, String a, String b) {
		return mined.stream().filter(s -> s.kind == Constraint.Kind.REQUIRES && s.a.equals(a) && b.equals(s.b)).findFirst().orElseThrow(() -> new AssertionError(a + " requires " + b + " not mined: " + mined));
	}
}
