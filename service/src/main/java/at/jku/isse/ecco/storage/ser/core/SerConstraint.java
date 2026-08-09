package at.jku.isse.ecco.storage.ser.core;

import at.jku.isse.ecco.core.Constraint;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link Constraint}.
 */
public class SerConstraint implements Constraint {

	public static final long serialVersionUID = 1L;


	private final String id;
	private final Kind kind;
	private final String featureA;
	private final String featureB; // null for MANDATORY


	public SerConstraint(Kind kind, String featureA, String featureB) {
		checkNotNull(kind);
		checkNotNull(featureA);
		this.kind = kind;
		this.featureA = featureA;
		this.featureB = featureB;
		this.id = Constraint.buildId(kind.name(), featureA, featureB);
	}


	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public Kind getKind() {
		return this.kind;
	}

	@Override
	public String getFeatureA() {
		return this.featureA;
	}

	@Override
	public String getFeatureB() {
		return this.featureB;
	}


	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof SerConstraint)) return false;

		final Constraint other = (Constraint) obj;
		return this.id.equals(other.getId());
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id);
	}

	@Override
	public String toString() {
		return this.id;
	}

}
