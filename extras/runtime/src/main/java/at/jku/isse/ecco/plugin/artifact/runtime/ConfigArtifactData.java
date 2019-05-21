package at.jku.isse.ecco.plugin.artifact.runtime;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class ConfigArtifactData implements ArtifactData {

	private String type, value;


	public ConfigArtifactData(String type, String value) {
		this.type = type;
		this.value = value;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final ConfigArtifactData other = (ConfigArtifactData) obj;
		return (type + value).equals(other.getType() + other.getValue());
	}

	@Override
	public int hashCode() {
		return Objects.hash(type + value);
	}

	public String getType() {
		return type;
	}

	public String getValue() {
		return value;
	}

	@Override
	public String toString() {
		return "\n-------------------" +
				"\nTYPE\t" + type +
				"\nVALUE\t" + value;
	}

}
