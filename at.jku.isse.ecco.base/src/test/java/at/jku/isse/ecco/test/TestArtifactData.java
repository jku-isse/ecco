package at.jku.isse.ecco.test;

import at.jku.isse.ecco.artifact.ArtifactData;

import static com.google.common.base.Preconditions.checkNotNull;

public class TestArtifactData implements ArtifactData {

	private String identifier;

	public TestArtifactData(String identifier) {
		checkNotNull(identifier);

		this.identifier = identifier;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof TestArtifactData)) return false;

		TestArtifactData that = (TestArtifactData) o;

		return identifier.equals(that.identifier);

	}

	@Override
	public int hashCode() {
		return identifier.hashCode();
	}

	@Override
	public String toString() {
		return this.identifier;
	}

}
