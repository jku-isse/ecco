package at.jku.isse.ecco.adapter.challenge.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class FieldArtifactData implements ArtifactData {

	private String field;

	public FieldArtifactData(String field) {
		this.field = field;
	}

	public void setField(String field) {
		this.field = field;
	}

	public String getField() {
		return this.field;
	}

	@Override
	public String toString() {
		return this.field;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.field);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FieldArtifactData other = (FieldArtifactData) obj;
		if (field == null) {
			if (other.field != null)
				return false;
		} else if (!field.equals(other.field))
			return false;
		return true;
	}

}
