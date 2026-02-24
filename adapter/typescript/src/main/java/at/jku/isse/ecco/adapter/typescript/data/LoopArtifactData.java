package at.jku.isse.ecco.adapter.typescript.data;

import java.util.Objects;

public class LoopArtifactData extends AbstractArtifactData {

    private final String loopBlock;

    public LoopArtifactData(String loopHead) {
        this.loopBlock = loopHead;
    }

    @Override
    public String toString() {
        return this.loopBlock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.loopBlock);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LoopArtifactData other = (LoopArtifactData) obj;
        if (loopBlock == null) {
            if (other.loopBlock != null)
                return false;
        } else if (!loopBlock.equals(other.loopBlock))
            return false;
        return true;
    }

}
