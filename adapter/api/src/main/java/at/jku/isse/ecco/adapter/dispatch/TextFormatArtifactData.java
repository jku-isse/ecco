package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The charset and line separator of a text file, as a child of the file's node after its lines and
 * its final newline: traced to features like a line, so each variant is written in the format it
 * was committed in - recorded once per file, the newest commit's charset and line separator were
 * written into every variant (a UTF-8 variant checked out in ISO-8859-1, LF as CRLF; see
 * TextFileFormat). Variants in the same format share one.
 * <p>
 * A file that mixes line separators also lists the lines that end with another separator than its
 * first, by their index in the variant ({@link #getOtherSeparators()}): it was written with the
 * first one throughout (gap #53). Null for a file with one separator - every format stored before.
 */
public class TextFormatArtifactData implements ArtifactData {

	private static final long serialVersionUID = 1L;

	private final String charset;
	private final String lineSeparator;
	private final TreeMap<Integer, String> otherSeparators;

	public TextFormatArtifactData(String charset, String lineSeparator) {
		this(charset, lineSeparator, null);
	}

	public TextFormatArtifactData(String charset, String lineSeparator, Map<Integer, String> otherSeparators) {
		this.charset = Objects.requireNonNull(charset);
		this.lineSeparator = Objects.requireNonNull(lineSeparator);
		this.otherSeparators = otherSeparators == null || otherSeparators.isEmpty() ? null : new TreeMap<>(otherSeparators);
	}

	/** The separator after each line (by its index) that does not end with {@link #getLineSeparator()}; empty if none. */
	public Map<Integer, String> getOtherSeparators() {
		return this.otherSeparators == null ? Map.of() : this.otherSeparators;
	}

	public String getCharset() {
		return this.charset;
	}

	public String getLineSeparator() {
		return this.lineSeparator;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TextFormatArtifactData other && this.charset.equals(other.charset) && this.lineSeparator.equals(other.lineSeparator)
				&& Objects.equals(this.otherSeparators, other.otherSeparators);
	}

	@Override
	public int hashCode() {
		// as before for a file with one separator
		return this.otherSeparators == null ? Objects.hash(TextFormatArtifactData.class.getName(), this.charset, this.lineSeparator)
				: Objects.hash(TextFormatArtifactData.class.getName(), this.charset, this.lineSeparator, this.otherSeparators);
	}

	@Override
	public String toString() {
		String separator = switch (this.lineSeparator) {
			case "\n" -> "LF";
			case "\r\n" -> "CRLF";
			case "\r" -> "CR";
			default -> this.lineSeparator;
		};
		return "(format: " + this.charset + ", " + separator + (this.otherSeparators == null ? "" : ", mixed") + ")";
	}
}
