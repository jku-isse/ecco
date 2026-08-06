package at.jku.isse.ecco.plugin.artifact.php;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

/**
 * @author Timea Kovacs
 */
public class PhpArtifactData implements ArtifactData {

	private Type type = Type.NONE;

	public enum Type {
		NONE, BASE, BLOCK, FUNCTION_OR_CLASS, PARAMETERS
	}

	private String value;

	public PhpArtifactData(String value) {
		this.value = value;
	}

	public PhpArtifactData() {
		this.value = "";
	}

	public void concatToValue(String s) {
		this.value += s + " ";
	}

	public String getValue() {
		return this.value;
	}

	public Type getType() {
		return this.type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.value);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PhpArtifactData other = (PhpArtifactData) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return this.value;
	}

}
