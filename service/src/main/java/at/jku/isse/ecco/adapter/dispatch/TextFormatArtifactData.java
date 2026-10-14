package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/**
 * The charset and line separator of a text file, as a child of the file's node after its lines and
 * its final newline: traced to features like a line, so each variant is written in the format it
 * was committed in - recorded once per file, the newest commit's charset and line separator were
 * written into every variant (a UTF-8 variant checked out in ISO-8859-1, LF as CRLF; see
 * TextFileFormat). Variants in the same format share one.
 */
public class TextFormatArtifactData implements ArtifactData {

	private static final long serialVersionUID = 1L;

	private final String charset;
	private final String lineSeparator;

	public TextFormatArtifactData(String charset, String lineSeparator) {
		this.charset = Objects.requireNonNull(charset);
		this.lineSeparator = Objects.requireNonNull(lineSeparator);
	}

	public String getCharset() {
		return this.charset;
	}

	public String getLineSeparator() {
		return this.lineSeparator;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TextFormatArtifactData other && this.charset.equals(other.charset) && this.lineSeparator.equals(other.lineSeparator);
	}

	@Override
	public int hashCode() {
		return Objects.hash(TextFormatArtifactData.class.getName(), this.charset, this.lineSeparator);
	}

	@Override
	public String toString() {
		String separator = switch (this.lineSeparator) {
			case "\n" -> "LF";
			case "\r\n" -> "CRLF";
			case "\r" -> "CR";
			default -> this.lineSeparator;
		};
		return "(format: " + this.charset + ", " + separator + ")";
	}
}
