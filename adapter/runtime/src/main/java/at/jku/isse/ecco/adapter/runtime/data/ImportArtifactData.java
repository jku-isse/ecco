package at.jku.isse.ecco.adapter.runtime.data;

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
		if (other.importName.contains("\t"))
			other.importName = other.importName.replace("\t","");
		if (importName.contains("\t"))
			importName = importName.replace("\t","");
		if (importName == null) {
			if (other.importName != null)
				return false;
		} else if (!importName.trim().equals(other.importName.trim()))
			return false;
		return true;
	}

}
