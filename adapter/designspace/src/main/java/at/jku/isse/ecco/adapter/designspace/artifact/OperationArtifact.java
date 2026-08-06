package at.jku.isse.ecco.adapter.designspace.artifact;

import at.jku.isse.designspace.sdk.core.operations.Operation;
import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class OperationArtifact implements ArtifactData {
    private final Operation operation;

    public OperationArtifact(Operation operation) {
        this.operation = operation;
    }

    public Operation getOperation() {
        return operation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationArtifact that = (OperationArtifact) o;
        return Objects.equals(operation, that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operation);
    }

    @Override
    public String toString() {
        return String.format("%s[ %s ]",
                super.toString(),
                operation.toString());
    }
}
