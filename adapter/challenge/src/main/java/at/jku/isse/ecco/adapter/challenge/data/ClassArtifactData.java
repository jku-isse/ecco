package at.jku.isse.ecco.adapter.challenge.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class ClassArtifactData implements ArtifactData {

	private String name;

	public ClassArtifactData(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return this.name;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		//if (getClass() != obj.getClass())
		//	return false;
		//ClassArtifactData other = (ClassArtifactData) obj;
		if (name == null) {
			if (obj.toString() != null)
				return false;
		} else if (!name.equals(obj.toString().replace("at.jku.isse.ecco.adapter.runtime.data","at.jku.isse.ecco.adapter.challenge.data")))
			return false;
		return true;
	}

}
