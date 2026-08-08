package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;

/**
 * A paragraph block. No content of its own beyond its {@code LineArtifactData} children (its raw text)
 * - a marker type, equal to any other paragraph, same as every other content-free container type here.
 * Disambiguation between distinct paragraphs, like between distinct content-equal lines elsewhere in
 * this codebase, comes from tree position/context, not artifact identity.
 */
public class ParagraphArtifactData implements ArtifactData {

	@Override
	public String toString() {
		return "(paragraph)";
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
