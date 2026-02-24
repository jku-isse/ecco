package at.jku.isse.ecco.adapter.typescript.data;

import java.util.Objects;

public class BlockArtifactData extends AbstractArtifactData {

	private final String block;


	public BlockArtifactData(String block) {
		this.block = block;
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
		if (block == null) {
            return other.block == null;
		} else return block.equals(other.block);
    }

}
