package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.artifact.ArtifactData;
import com.github.javaparser.ast.stmt.BlockStmt;

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
        if (block == null) {
            if (other.block != null)
                return false;
        } else if (!block.equals(other.block))
            return false;
        return true;
    }
}
