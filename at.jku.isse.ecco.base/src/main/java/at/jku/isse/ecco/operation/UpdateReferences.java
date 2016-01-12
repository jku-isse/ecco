package at.jku.isse.ecco.operation;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.tree.Node;

import java.util.Optional;

/**
 * Updates the replacing by property in the artifact tree by the replaced artifacts.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class UpdateReferences extends AbstractUnaryTreeOperation<Void> {

	@Override
	protected void prefix(Node node) {
		assert node != null : "Expected non-null node but was null";

		final Artifact<?> artifact = node.getArtifact();

		if (node.isUnique() && artifact != null) {
			updateUses(artifact);
			updateUsedBy(artifact);
		}
	}

	/**
	 * Updates the <code>usedBy</code> of given artifact by setting the replacing artifact as sources.
	 *
	 * @param artifact that should be updated
	 */
	private void updateUsedBy(final Artifact<?> artifact) {
		assert artifact != null : "Expected non-null artifact but was null";

		for (ArtifactReference usedBy : artifact.getUsedBy()) {
			Optional<Artifact<?>> replacingArtifactOptional = usedBy.getSource().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT);

			if (replacingArtifactOptional.isPresent()) {
				Artifact<?> replacingArtifact = replacingArtifactOptional.get();
				usedBy.setSource(replacingArtifact);
				if (!replacingArtifact.getUses().contains(usedBy)) {
					replacingArtifact.getUses().add(usedBy);
				}
			}
		}
	}

	/**
	 * Updates the <code>uses</code> of the artifact by setting the replacing artifact as target.
	 *
	 * @param artifact that should be updated
	 */
	private void updateUses(final Artifact<?> artifact) {
		assert artifact != null : "Expected non-null artifact but was null";

		for (ArtifactReference uses : artifact.getUses()) {
			Optional<Artifact<?>> replacingArtifactOptional = uses.getTarget().getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT);

			if (replacingArtifactOptional.isPresent()) {
				Artifact<?> replacingArtifact = replacingArtifactOptional.get();
				uses.setTarget(replacingArtifact);
				if (!replacingArtifact.getUsedBy().contains(uses)) {
					replacingArtifact.getUsedBy().add(uses);
				}
			}
		}
	}

}
