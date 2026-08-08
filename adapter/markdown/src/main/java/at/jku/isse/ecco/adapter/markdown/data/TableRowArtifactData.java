package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/**
 * One row of a GFM table (header or body), at row granularity - not per-cell (see the adapter's design
 * notes for why). Wraps a single {@code LineArtifactData} child for its own source line.
 */
public class TableRowArtifactData implements ArtifactData {

	private final boolean header;

	public TableRowArtifactData(boolean header) {
		this.header = header;
	}

	public boolean isHeader() {
		return this.header;
	}

	@Override
	public String toString() {
		return this.header ? "(table header row)" : "(table row)";
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.header);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		TableRowArtifactData other = (TableRowArtifactData) obj;
		return this.header == other.header;
	}

}
