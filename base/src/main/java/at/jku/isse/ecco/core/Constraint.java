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

	/**
	 * What a constraint between feature names says, defined once: its clause (the feature model the
	 * minimization and the checks use), when a selection violates it, and how it reads.
	 */
	enum Kind {
		/** A requires B: ~A | B. */
		REQUIRES,
		/** A excludes B: ~A | ~B. */
		EXCLUDES,
		/** A is in every configuration: A (B is null). */
		MANDATORY;

		/** The clause over the feature-name variables a and b. */
		public org.logicng.formulas.Formula clause(org.logicng.formulas.FormulaFactory f, String a, String b) {
			return switch (this) {
				case REQUIRES -> f.or(f.literal(a, false), f.literal(b, true));
				case EXCLUDES -> f.or(f.literal(a, false), f.literal(b, false));
				case MANDATORY -> f.literal(a, true);
			};
		}

		/** Whether [selected] feature names violate the constraint on a and b. */
		public boolean isViolatedBy(java.util.Set<String> selected, String a, String b) {
			return switch (this) {
				case REQUIRES -> selected.contains(a) && !selected.contains(b);
				case EXCLUDES -> selected.contains(a) && selected.contains(b);
				case MANDATORY -> !selected.contains(a);
			};
		}

		/** The constraint as people read it: "REQUIRES a -> b", "EXCLUDES a / b", "MANDATORY a". */
		public String describe(String a, String b) {
			return switch (this) {
				case REQUIRES -> "REQUIRES " + a + " -> " + b;
				case EXCLUDES -> "EXCLUDES " + a + " / " + b;
				case MANDATORY -> "MANDATORY " + a;
			};
		}

		/** Why a selection violates it - see {@link #isViolatedBy}. */
		public String violation(String a, String b) {
			return switch (this) {
				case REQUIRES -> describe(a, b) + " violated: " + a + " is selected without " + b;
				case EXCLUDES -> describe(a, b) + " violated: both are selected";
				case MANDATORY -> describe(a, b) + " violated: not selected";
			};
		}
	}

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
	 * @param kindName {@link Kind#name()}, as a plain string: callers build ids from stored or
	 *                 suggested kinds by name.
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
