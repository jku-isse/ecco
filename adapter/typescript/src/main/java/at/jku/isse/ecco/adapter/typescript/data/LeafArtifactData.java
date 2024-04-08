package at.jku.isse.ecco.adapter.typescript.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class LeafArtifactData extends AbstractArtifactData {

	private String line;

	public LeafArtifactData(String line) {
		this.line = line;
	}

	public String getLine() {
		return this.line;
	}

	@Override
	public String toString() {
		return this.line;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.line);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LeafArtifactData other = (LeafArtifactData) obj;
		if (line == null) {
			if (other.line != null)
				return false;
		} else if (!line.equals(other.line))
			return false;
		return true;
	}

}
