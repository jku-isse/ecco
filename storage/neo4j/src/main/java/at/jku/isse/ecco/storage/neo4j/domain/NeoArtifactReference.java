package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import org.neo4j.ogm.annotation.*;

import static com.google.common.base.Preconditions.checkNotNull;

@NodeEntity
public class NeoArtifactReference extends NeoEntity implements ArtifactReference, ArtifactReference.Op {

    @Property("type")
	private final String type;

    @Relationship("hasSourceARf")
	private Artifact.Op<?> source;

    @Relationship("hasTargetARf")
	private Artifact.Op<?> target;

	/**
	 * Constructs a new artifact reference with the type initiliazed to an empty string.
	 */
	public NeoArtifactReference() {
		this(null);
	}

	/**
	 * Constructs a new artifact reference with the given type.
	 *
	 * @param type The type (arbitrary string) of the artifact reference.
	 */
	public NeoArtifactReference(final String type) {
		this.type = type;
	}

	@Override
	public String getType() {
		return this.type;
	}

	@Override
	public Artifact.Op<?> getSource() {
		return this.source;
	}

	@Override
	public Artifact.Op<?> getTarget() {
		return this.target;
	}

	@Override
	public void setSource(final Artifact.Op<?> source) {
		checkNotNull(source);

		this.source = source;
	}

	@Override
	public void setTarget(final Artifact.Op<?> target) {
		checkNotNull(target);

		this.target = target;
	}

	@Override
	public int hashCode() {
		int result = this.type != null ? this.type.hashCode() : 0;
		result = 31 * result + (this.source != null ? this.source.hashCode() : 0);
		result = 31 * result + (this.target != null ? this.target.hashCode() : 0);
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		NeoArtifactReference that = (NeoArtifactReference) o;

		if (this.type != null ? !this.type.equals(that.type) : that.type != null) return false;
		if (this.source != null ? !this.source.equals(that.source) : that.source != null) return false;
		return !(this.target != null ? !this.target.equals(that.target) : that.target != null);
	}

	@Override
	public String toString() {
		return "[" + this.source + " > " + this.target + "]";
	}

}
