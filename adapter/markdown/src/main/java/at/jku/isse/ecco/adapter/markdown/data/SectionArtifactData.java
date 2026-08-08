package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/**
 * A heading-grouped section - NOT a CommonMark AST concept (a {@code Heading} there is a sibling leaf
 * block, not a container); this is a grouping this adapter adds on top, so a whole section can be a
 * single feature-conditional unit. Wraps the raw heading line verbatim (for byte-exact reconstruction,
 * same principle as every leaf type here) plus the heading level, used only to decide nesting while
 * reading - never needed for writing.
 */
public class SectionArtifactData implements ArtifactData {

	private final int level;
	private final String headingLine;

	public SectionArtifactData(int level, String headingLine) {
		this.level = level;
		this.headingLine = headingLine;
	}

	public int getLevel() {
		return this.level;
	}

	public String getHeadingLine() {
		return this.headingLine;
	}

	@Override
	public String toString() {
		return this.headingLine;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.level, this.headingLine);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		SectionArtifactData other = (SectionArtifactData) obj;
		return this.level == other.level && Objects.equals(this.headingLine, other.headingLine);
	}

}
