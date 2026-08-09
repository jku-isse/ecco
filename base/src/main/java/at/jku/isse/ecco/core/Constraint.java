package at.jku.isse.ecco.core;

import at.jku.isse.ecco.dao.Persistable;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * A feature-model constraint accepted by a human reviewer (see {@code ConstraintMiner} in
 * {@code service}, which mines candidates from commit history). Persisted in the repository itself
 * so it travels with fork/pull/push, unlike the local, per-machine tracking this replaces.
 *
 * <p>Purely advisory bookkeeping: a persisted {@code Constraint} records that a human reviewed and
 * accepted a suggestion, nothing more. It does not block or gate {@code commit()}/{@code checkout()}
 * -- accepted constraints are only ever consulted to decide which surplus warnings are non-actionable
 * noise (see {@code SurplusModuleSuppressor}/{@code SurplusLatticeAbsorber} in {@code service}), and
 * only after being re-verified against freshly mined data every time (a stored acceptance is never
 * trusted on its own -- see {@code EccoService#acceptedSuggestions}).
 */
public interface Constraint extends Persistable {

	enum Kind { REQUIRES, EXCLUDES, MANDATORY }

	/**
	 * The natural id: {@code kind.name() + "|" + featureA + "|" + (featureB == null ? "" : featureB)},
	 * with featureA/featureB URL-encoded (see {@link #buildId}).
	 *
	 * @return The id.
	 */
	String getId();

	/**
	 * Builds the natural id documented on {@link #getId()} -- the one shared place this
	 * construction should happen. featureA/featureB are URL-encoded: feature names are unrestricted
	 * free text, and a name containing a literal "|" would otherwise be misparsed as a field
	 * separator by a caller splitting the id back apart, or (for a "," specifically) corrupt a
	 * caller that joins multiple ids with "," (e.g. {@code ConstraintSuggestionPreferences}).
	 *
	 * @param kindName Kind#name(), passed as a plain string so this isn't tied to any one Kind enum
	 *                 (service's {@code ConstraintMiner.Kind} has the identical constant names but
	 *                 is a distinct type from this interface's own {@link Kind}).
	 */
	static String buildId(String kindName, String featureA, String featureB) {
		return kindName + "|" + URLEncoder.encode(featureA, StandardCharsets.UTF_8) + "|"
				+ (featureB == null ? "" : URLEncoder.encode(featureB, StandardCharsets.UTF_8));
	}

	/** Inverse of the featureA/featureB half of {@link #buildId} -- decodes one already-split field. */
	static String decodeIdPart(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	/**
	 * @return The kind of constraint.
	 */
	Kind getKind();

	/**
	 * Antecedent / first feature / mandatory feature, by name (not a feature object reference --
	 * mirrors {@code ConstraintMiner.Suggestion}, which is feature-name-level only).
	 *
	 * @return The name of the first feature.
	 */
	String getFeatureA();

	/**
	 * Consequent / second feature, by name; null for {@link Kind#MANDATORY}.
	 *
	 * @return The name of the second feature, or null.
	 */
	String getFeatureB();

	@Override
	int hashCode();

	@Override
	boolean equals(Object object);

}
