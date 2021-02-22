package at.jku.isse.ecco.adapter.cpp.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class IncludeArtifactData implements ArtifactData {

	private String includeName;

	public IncludeArtifactData(String importName) {
		this.includeName = importName;
	}

	public void setImportName(String importName) {
		this.includeName = importName;
	}

	public String getImportName() {
		return this.includeName;
	}

	@Override
	public String toString() {
		return this.includeName;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.includeName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IncludeArtifactData other = (IncludeArtifactData) obj;
		if (includeName == null) {
			if (other.includeName != null)
				return false;
		} else if (!includeName.equals(other.includeName))
			return false;
		return true;
	}

}
