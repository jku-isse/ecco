package at.jku.isse.ecco.adapter.lilypond.data.token;

import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;

import java.util.Objects;

public class VariableDefinitionArtifactData extends DefaultTokenArtifactData {

    private final String variableName;

    public VariableDefinitionArtifactData(ParceToken token) {
        super(token);

        if (token.getTransformationData() instanceof String name) {
            variableName = name;
        } else {
            variableName = token.getText();
        }
    }

    public String getVariableName() {
        return variableName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        VariableDefinitionArtifactData that = (VariableDefinitionArtifactData) o;
        return variableName.equals(that.variableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), variableName);
    }

    @Override
    public String toString() {
        return "VariableDefinitionArtifactData{" +
                "variableName='" + variableName + "', " + super.toString() + "}";
    }
}
