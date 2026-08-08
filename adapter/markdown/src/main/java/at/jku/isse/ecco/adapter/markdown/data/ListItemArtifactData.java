package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;

/** One item of a bullet or ordered list, wrapping its own recursively-translated content (which can include nested lists, code blocks, etc.). Marker type - see {@link ParagraphArtifactData}'s javadoc for why. */
public class ListItemArtifactData implements ArtifactData {

	@Override
	public String toString() {
		return "(list item)";
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
