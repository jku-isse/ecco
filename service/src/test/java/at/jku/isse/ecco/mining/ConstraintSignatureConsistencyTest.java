package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.storage.ser.core.SerConstraint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * ConstraintSuggestionPreferences.signatureOf()'s "kind|a|b" format is duplicated in three places
 * that must all produce byte-for-byte identical strings for the same kind/a/b: this method,
 * AcceptedConstraints.acceptedSignatures() (built from persisted Constraints), and SerConstraint's
 * own id (used for equals()/hashCode() and Repository.merge()'s cross-repository dedup). All three
 * previously built the format independently with no escaping, so a feature name containing a literal
 * "|" (misparsed by parseSignature()'s split) or "," (splits one signature into two when
 * comma-joined by ConstraintSuggestionPreferences.writeSet()) corrupted the data. Fixed by routing
 * all three through the one shared Constraint.buildId() helper.
 */
public class ConstraintSignatureConsistencyTest {

	@Test
	public void allThreeSignatureBuildersProduceIdenticalStringsForFeatureNamesWithDelimiterCharacters() {
		String featureA = "Feature|With|Pipes";
		String featureB = "Feature,With,Commas";

		ConstraintMiner.Suggestion suggestion = new ConstraintMiner.Suggestion(
				ConstraintMiner.Kind.REQUIRES, featureA, featureB, 1.0, 1.0, 4, List.of());
		SerConstraint constraint = new SerConstraint(Constraint.Kind.REQUIRES, featureA, featureB);

		String fromSuggestion = ConstraintSuggestionPreferences.signatureOf(suggestion);
		String fromAcceptedConstraints = AcceptedConstraints.acceptedSignatures(List.of(constraint)).iterator().next();
		String fromConstraintId = constraint.getId();

		assertEquals(fromSuggestion, fromAcceptedConstraints,
				"signatureOf() and acceptedSignatures() must match, or 'already accepted' detection breaks");
		assertEquals(fromSuggestion, fromConstraintId,
				"signatureOf() and SerConstraint.getId() must match, or Repository.merge()'s dedup breaks");
	}

	@Test
	public void signatureRoundTripsThroughPipesWithoutMisassigningFields() {
		String featureA = "Feature|With|Pipes";
		String featureB = "Feature|B";
		ConstraintMiner.Suggestion suggestion = new ConstraintMiner.Suggestion(
				ConstraintMiner.Kind.EXCLUDES, featureA, featureB, 1.0, 1.0, 4, List.of());

		String signature = ConstraintSuggestionPreferences.signatureOf(suggestion);
		ConstraintSuggestionPreferences.AcceptedConstraint parsed = ConstraintSuggestionPreferences.parseSignature(signature);

		assertEquals(ConstraintMiner.Kind.EXCLUDES.name(), parsed.kind.name());
		assertEquals(featureA, parsed.a);
		assertEquals(featureB, parsed.b);
	}

	@Test
	public void signatureContainingACommaHasNoRawCommaLeftToCorruptCommaJoinedStorage() {
		String featureA = "Feature, Extended";
		ConstraintMiner.Suggestion suggestion = new ConstraintMiner.Suggestion(
				ConstraintMiner.Kind.MANDATORY, featureA, null, 1.0, 1.0, 4, List.of());

		String signature = ConstraintSuggestionPreferences.signatureOf(suggestion);

		assertFalse(signature.contains(","),
				"a raw ',' in the signature would be split into two garbage entries by writeSet()'s String.join(\",\", ...) / readSet()'s split(\",\")");
	}
}
