package at.jku.isse.ecco.storage.ser.artifact;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link ArtifactReference}.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public class SerArtifactReference implements ArtifactReference, ArtifactReference.Op {

	public static final long serialVersionUID = 1L;


	private final String type;

	// transient for the same reason as SerArtifact.containingNode: source/target can point to an
	// artifact that lives in a different association's tree, so a direct reference would pull in a
	// foreign object graph via a side channel on reload. The *Id fields are the serialized
	// surrogates; the transient fields are populated by SerTransactionStrategy's post-load
	// resolution pass against a global artifact-id index.
	private transient Artifact.Op<?> source;
	private transient Artifact.Op<?> target;
	private String sourceId;
	private String targetId;

	/**
	 * Constructs a new artifact reference with the type initiliazed to an empty string.
	 */
	public SerArtifactReference() {
		this("");
	}

	/**
	 * Constructs a new artifact reference with the given type.
	 *
	 * @param type The type (arbitrary string) of the artifact reference.
	 */
	public SerArtifactReference(final String type) {
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
		this.sourceId = (source instanceof SerArtifact<?> serArtifact) ? serArtifact.getStorageId() : null;
	}

	@Override
	public void setTarget(final Artifact.Op<?> target) {
		checkNotNull(target);

		this.target = target;
		this.targetId = (target instanceof SerArtifact<?> serArtifact) ? serArtifact.getStorageId() : null;
	}

	public String getSourceId() {
		return this.sourceId;
	}

	public String getTargetId() {
		return this.targetId;
	}

	/** Used only by SerTransactionStrategy's post-load resolution pass - sets the live references without touching sourceId/targetId (already correct, just loaded from the stream). */
	public void resolveReferences(final Artifact.Op<?> source, final Artifact.Op<?> target) {
		this.source = source;
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

		SerArtifactReference that = (SerArtifactReference) o;

		if (this.type != null ? !this.type.equals(that.type) : that.type != null) return false;
		if (this.source != null ? !this.source.equals(that.source) : that.source != null) return false;
		return !(this.target != null ? !this.target.equals(that.target) : that.target != null);
	}

	@Override
	public String toString() {
		return "[" + this.source + " > " + this.target + "]";
	}

}
