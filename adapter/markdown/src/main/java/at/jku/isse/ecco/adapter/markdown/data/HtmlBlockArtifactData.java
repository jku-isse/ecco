package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/** A raw HTML block embedded in the document. Wraps its raw source lines verbatim as {@code LineArtifactData} children; the literal here (CommonMark's own concatenated copy) is diagnostic/distinguishing only. */
public class HtmlBlockArtifactData implements ArtifactData {

	private final String literal;

	public HtmlBlockArtifactData(String literal) {
		this.literal = literal;
	}

	public String getLiteral() {
		return this.literal;
	}

	@Override
	public String toString() {
		return this.literal != null ? this.literal : "(html block)";
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.literal);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		HtmlBlockArtifactData other = (HtmlBlockArtifactData) obj;
		return Objects.equals(this.literal, other.literal);
	}

}
