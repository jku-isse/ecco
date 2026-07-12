package at.jku.isse.ecco.core;

import at.jku.isse.ecco.dao.Persistable;

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
	 * The natural id: {@code kind.name() + "|" + featureA + "|" + (featureB == null ? "" : featureB)}.
	 *
	 * @return The id.
	 */
	String getId();

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
