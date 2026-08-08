package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;

/** A {@code >}-quoted block, wrapping its own recursively-translated nested blocks. Marker type - see {@link ParagraphArtifactData}'s javadoc for why. */
public class BlockQuoteArtifactData implements ArtifactData {

	@Override
	public String toString() {
		return "(block quote)";
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && getClass() == obj.getClass();
	}

}
