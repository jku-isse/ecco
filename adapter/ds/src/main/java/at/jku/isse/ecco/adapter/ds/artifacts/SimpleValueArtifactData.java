package at.jku.isse.ecco.adapter.ds.artifacts;

import at.jku.isse.designspace.variant.dto.*;
import lombok.Getter;

import java.util.Objects;

@Getter
public class SimpleValueArtifactData<T> implements ValueArtifactData {

        private final T value;

    public SimpleValueArtifactData(SimpleValueDto<T> simpleValueDto) {
        this.value = simpleValueDto.getValue();
    }

    @Override
    public String toString() {
        return "<SimpleValueArtifactData<"+ this.value.getClass() + ">{" +
                "value = " + this.value +
                "}>";
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

        SimpleValueArtifactData other = (SimpleValueArtifactData) obj;
        return (Objects.equals(this.value, other.value));
    }
}
