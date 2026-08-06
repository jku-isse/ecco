package at.jku.isse.ecco.plugin.artifact.runtime;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class RuntimeArtifactData implements ArtifactData {

	private String file;
	private long lineNumber;


	public RuntimeArtifactData(String file, long lineNumber) {
		this.file = file;
		this.lineNumber = lineNumber;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final RuntimeArtifactData other = (RuntimeArtifactData) obj;
		return (file + lineNumber).equals(other.getFile() + other.getLineNumber());
	}

	@Override
	public int hashCode() {
		return Objects.hash(file + lineNumber);
	}

	public String getFile() {
		return file;
	}

	public long getLineNumber() {
		return lineNumber;
	}

	@Override
	public String toString() {
		return "\n-------------------" +
				"\nFILE\t" + file +
				"\nLN\t" + lineNumber;
	}

}
