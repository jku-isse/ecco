package at.jku.isse.ecco.adapter.typescript.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class IfBlockArtifactData implements ArtifactData {

	private String block;

	public IfBlockArtifactData(String block) {
		this.block = block;
	}

	public String getBlock() {
		return this.block;
	}

	@Override
	public String toString() {
		return this.block;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.block);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IfBlockArtifactData other = (IfBlockArtifactData) obj;
		if (block == null) {
			if (other.block != null)
				return false;
		} else if (!block.equals(other.block))
			return false;
		return true;
	}

}
