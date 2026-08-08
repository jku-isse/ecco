package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/** A single raw source line, verbatim (no trailing line terminator) - same shape as the text/C adapters' own line type, kept as a local copy rather than shared (matching their existing precedent). */
public class LineArtifactData implements ArtifactData {

	private final String line;

	public LineArtifactData(String line) {
		this.line = line;
	}

	public String getLine() {
		return this.line;
	}

	@Override
	public String toString() {
		return this.line;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.line);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		LineArtifactData other = (LineArtifactData) obj;
		return Objects.equals(this.line, other.line);
	}

}
