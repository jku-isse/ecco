package at.jku.isse.ecco.artifact;

import at.jku.isse.ecco.EccoException;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An operator that performs operations on artifact operands (see {@link at.jku.isse.ecco.artifact.Artifact.Op}).
 */
public class ArtifactOperator {

	private Artifact.Op<?> artifact;

	public ArtifactOperator(Artifact.Op artifact) {
		this.artifact = artifact;
	}


	public void checkConsistency() {
		for (ArtifactReference.Op uses : this.artifact.getUses()) {
			if (uses.getSource() != this.artifact)
				throw new EccoException("Source of uses artifact reference must be identical to artifact.");
			for (ArtifactReference.Op usedBy : uses.getTarget().getUsedBy()) {
				if (usedBy.getSource() == this.artifact) {
					if (uses != usedBy)
						throw new EccoException("Artifact reference instance must be identical in source and target.");
				}
			}
		}
	}


	public boolean hasReplacingArtifact() {
		return this.artifact.<Artifact.Op<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent();
	}

	public Artifact.Op<?> getReplacingArtifact() {
		if (this.hasReplacingArtifact())
			return this.artifact.<Artifact.Op<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
		else
			return null;
	}

	public void setReplacingArtifact(Artifact.Op<?> replacingArtifact) {
		checkNotNull(replacingArtifact);
		this.artifact.putProperty(Artifact.PROPERTY_REPLACING_ARTIFACT, replacingArtifact);
	}


//	// TODO: either move this into NodeOperator or remove it entirely.
//	public void sequence(Artifact.Op<?> other) {
//		checkNotNull(other);
//
//		if (!this.artifact.isOrdered() || !other.isOrdered())
//			throw new EccoException("Artifacts must be ordered for sequencing.");
//		if (!this.artifact.equals(other))
//			throw new EccoException("Artifacts must be equal for sequencing.");
//
//		if (this.artifact.isSequenced() && other.isSequenced() && this.artifact.getSequenceGraph() != other.getSequenceGraph()) {
//			this.artifact.getSequenceGraph().sequence(other.getSequenceGraph());
//			other.setSequenceGraph(this.artifact.getSequenceGraph());
//		} else if (!this.artifact.isSequenced() && !other.isSequenced()) {
//			this.artifact.setSequenceGraph(this.artifact.createSequenceGraph());
//			this.artifact.getSequenceGraph().sequence(left);
//		}
//
//		if (this.artifact.isSequenced() && !other.isSequenced()) {
//			this.artifact.getSequenceGraph().sequence(right);
//		} else if (!this.artifact.isSequenced() && other.isSequenced()) {
//			other.getSequenceGraph().sequence(left);
//			this.artifact.setSequenceGraph(other.getSequenceGraph());
//			throw new EccoException("Left node was not sequenced but right node was!");
//		}
//	}


	// # REFERENCES ####################################################################################################

	public boolean uses(Artifact.Op<?> target) {
		for (ArtifactReference.Op uses : this.artifact.getUses()) {
			if (uses.getTarget() == target) {
				return true;
			}
		}
		return false;
	}


	public void updateArtifactReferences() {
		// update "uses" artifact references
		for (ArtifactReference.Op uses : this.artifact.getUses()) {
			if (uses.getSource() != this.artifact)
				throw new EccoException("Source of uses artifact reference must be identical to artifact.");

			if (uses.getTarget().hasReplacingArtifact()) {
				Artifact.Op<?> replacingArtifact = uses.getTarget().getReplacingArtifact();
				if (replacingArtifact != null) {
					uses.setTarget(replacingArtifact);
					if (!replacingArtifact.getUsedBy().contains(uses)) {
						replacingArtifact.addUsedBy(uses);
					}
				}
			}
		}

		// update "used by" artifact references
		for (ArtifactReference.Op usedBy : this.artifact.getUsedBy()) {
			if (usedBy.getTarget() != this.artifact)
				throw new EccoException("Target of usedBy artifact reference must be identical to artifact.");

			if (usedBy.getSource().hasReplacingArtifact()) {
				Artifact.Op<?> replacingArtifact = usedBy.getSource().getReplacingArtifact();
				if (replacingArtifact != null) {
					usedBy.setSource(replacingArtifact);
					if (!replacingArtifact.getUses().contains(usedBy)) {
						replacingArtifact.addUses(usedBy);
					}
				}
			}
		}

		// update sequence graph symbols (which are artifacts)
		if (this.artifact.getSequenceGraph() != null) {
			this.artifact.getSequenceGraph().updateArtifactReferences();
		}
	}


	// # PROPERTIES ####################################################################################################

	public <T> Optional<T> getProperty(final String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected non-empty name, but was empty.");

		Optional<T> result = Optional.empty();
		if (this.artifact.getProperties().containsKey(name)) {
			final Object obj = this.artifact.getProperties().get(name);
			try {
				@SuppressWarnings("unchecked")
				final T item = (T) obj;
				result = Optional.of(item);
			} catch (final ClassCastException e) {
				System.err.println("Expected a different type of the property.");
			}
		}

		return result;
	}

	public <T> void putProperty(final String name, final T property) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected non-empty name, but was empty.");
		checkNotNull(property);

		this.artifact.getProperties().put(name, property);
	}

	public void removeProperty(String name) {
		checkNotNull(name);

		this.artifact.getProperties().remove(name);
	}

}
