package at.jku.isse.ecco.artifact;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;

import static com.google.common.base.Preconditions.checkNotNull;

public class BaseArtifactReference implements ArtifactReference {

	private final String type;

	private Artifact source;
	private Artifact target;

	/**
	 * Constructs a new artifact reference with the type initiliazed to an empty
	 * string.
	 */
	public BaseArtifactReference() {
		this("");
	}

	/**
	 * Constructs a new artifact reference with the given type.
	 */
	public BaseArtifactReference(final String type) {
		this.type = type;
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
	public String getType() {
		return type;
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

}
