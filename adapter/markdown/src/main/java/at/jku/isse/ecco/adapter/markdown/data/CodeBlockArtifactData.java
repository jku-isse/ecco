package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/**
 * A fenced (```` ``` ````) or indented (4-space) code block. Wraps its raw source lines verbatim as
 * {@code LineArtifactData} children, including the fence delimiter lines themselves for a fenced block
 * - no special-casing needed on the write side, same principle as every other leaf type here.
 */
public class CodeBlockArtifactData implements ArtifactData {

	private final boolean fenced;
	private final String info;

	public CodeBlockArtifactData(boolean fenced, String info) {
		this.fenced = fenced;
		this.info = info;
	}

	public boolean isFenced() {
		return this.fenced;
	}

	/** The fenced code block's info string (e.g. a language tag like {@code java}) - {@code null} for an indented code block, or a fenced one with none. */
	public String getInfo() {
		return this.info;
	}

	@Override
	public String toString() {
		return (this.fenced ? "```" : "(indented)") + (this.info != null && !this.info.isEmpty() ? this.info : "");
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.fenced, this.info);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		CodeBlockArtifactData other = (CodeBlockArtifactData) obj;
		return this.fenced == other.fenced && Objects.equals(this.info, other.info);
	}

}
