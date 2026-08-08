package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;

/**
 * A GFM table, wrapping {@code TableRowArtifactData} children (header row(s) then body rows, in
 * document order - CommonMark's own {@code TableHead}/{@code TableBody} grouping nodes aren't kept as
 * separate ECCO containers, since {@code TableRowArtifactData}'s own header flag already carries that
 * distinction). Marker type - see {@link ParagraphArtifactData}'s javadoc for why.
 */
public class TableArtifactData implements ArtifactData {

	@Override
	public String toString() {
		return "(table)";
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
