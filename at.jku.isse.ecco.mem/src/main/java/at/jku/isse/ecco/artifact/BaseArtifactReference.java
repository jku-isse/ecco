package at.jku.isse.ecco.artifact;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link ArtifactReference}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class BaseArtifactReference implements ArtifactReference {

	private final String type;

	private Artifact source;
	private Artifact target;

	/**
	 * Constructs a new artifact reference with the type initiliazed to an empty string.
	 */
	public BaseArtifactReference() {
		this(null);
	}

	/**
	 * Constructs a new artifact reference with the given type.
	 */
	public BaseArtifactReference(final String type) {
		this.type = type;
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public Artifact getSource() {
		return source;
	}

	@Override
	public Artifact getTarget() {
		return target;
	}

	@Override
	public void setSource(final Artifact source) {
		checkNotNull(source);

		this.source = source;
	}

	@Override
	public void setTarget(final Artifact target) {
		checkNotNull(target);

		this.target = target;
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + (type != null ? type.hashCode() : 0);
		result = 31 * result + (source != null ? source.hashCode() : 0);
		result = 31 * result + (target != null ? target.hashCode() : 0);
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		//if (!super.equals(o)) return false;

		BaseArtifactReference that = (BaseArtifactReference) o;

		if (type != null ? !type.equals(that.type) : that.type != null) return false;
		if (source != null ? !source.equals(that.source) : that.source != null) return false;
		return !(target != null ? !target.equals(that.target) : that.target != null);
	}

}
