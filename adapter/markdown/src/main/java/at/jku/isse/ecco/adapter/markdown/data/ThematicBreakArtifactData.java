package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/** A thematic break (e.g. {@code ---}, {@code ***}, {@code ___}). Wraps a single {@code LineArtifactData} child; the literal here is diagnostic/distinguishing only, not needed for reconstruction. */
public class ThematicBreakArtifactData implements ArtifactData {

	private final String literal;

	public ThematicBreakArtifactData(String literal) {
		this.literal = literal;
	}

	public String getLiteral() {
		return this.literal;
	}

	@Override
	public String toString() {
		return this.literal != null ? this.literal : "(thematic break)";
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.literal);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		ThematicBreakArtifactData other = (ThematicBreakArtifactData) obj;
		return Objects.equals(this.literal, other.literal);
	}

}
