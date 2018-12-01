package at.jku.isse.ecco.artifact;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

public class StringArtifactData implements ArtifactData {

	private String value;

	public StringArtifactData(String value) {
		checkNotNull(value);
		this.value = value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		StringArtifactData that = (StringArtifactData) o;
		return Objects.equals(value, that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(value);
	}

	@Override
	public String toString() {
		return this.value;
	}

}
