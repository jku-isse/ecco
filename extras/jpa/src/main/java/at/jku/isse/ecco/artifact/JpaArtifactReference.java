package at.jku.isse.ecco.artifact;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.io.Serializable;

import static com.google.common.base.Preconditions.checkNotNull;

@Entity
public class JpaArtifactReference implements ArtifactReference, Serializable {

	private final String type;

	@Id
	@ManyToOne(targetEntity = JpaArtifact.class)
	private Artifact source;
	@Id
	@ManyToOne(targetEntity = JpaArtifact.class)
	private Artifact target;

	/**
	 * Constructs a new artifact reference with the type initiliazed to an empty
	 * string.
	 */
	public JpaArtifactReference() {
		this("");
	}

	/**
	 * Constructs a new artifact reference with the given type.
	 */
	public JpaArtifactReference(final String type) {
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
