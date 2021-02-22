package at.jku.isse.ecco.adapter.runtime.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class BlockArtifactData implements ArtifactData {

	private String block;

	protected BlockArtifactData() {
		this.block = null;
	}

	public BlockArtifactData(String block) {
		this.block = block;
	}

	public void setBlock(String block) {
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
		BlockArtifactData other = (BlockArtifactData) obj;
		if (other.block.contains("\t"))
			other.block = other.block.replace("\t","");
		if (block.contains("\t"))
			block = block.replace("\t","");
		if (block == null) {
			if (other.block != null)
				return false;
		} else if (!block.trim().equals(other.block.trim()))
			return false;
		return true;
	}

}
