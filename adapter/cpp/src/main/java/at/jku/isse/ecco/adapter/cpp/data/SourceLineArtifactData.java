package at.jku.isse.ecco.adapter.cpp.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/** One line of a C++ file, exactly as it is (see CppReader). */
public class SourceLineArtifactData implements ArtifactData {

	private static final long serialVersionUID = 1L;

	private final String line;

	public SourceLineArtifactData(String line) {
		this.line = Objects.requireNonNull(line);
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
		return this.line.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj || obj instanceof SourceLineArtifactData other && this.line.equals(other.line);
	}
}
