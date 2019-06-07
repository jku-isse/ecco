package at.jku.isse.ecco.adapter.java.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class ImportArtifactData implements ArtifactData {

	private String importName;

	public ImportArtifactData(String importName) {
		this.importName = importName;
	}

	public void setImportName(String importName) {
		this.importName = importName;
	}

	public String getImportName() {
		return this.importName;
	}

	@Override
	public String toString() {
		return this.importName;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.importName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ImportArtifactData other = (ImportArtifactData) obj;
		if (importName == null) {
			if (other.importName != null)
				return false;
		} else if (!importName.equals(other.importName))
			return false;
		return true;
	}

}
