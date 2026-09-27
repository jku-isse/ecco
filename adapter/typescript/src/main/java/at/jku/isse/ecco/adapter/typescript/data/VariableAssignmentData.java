package at.jku.isse.ecco.adapter.typescript.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class VariableAssignmentData extends AbstractArtifactData {

    private static final long serialVersionUID = 763234648826440872L;
    private String id, leadingText;

    public VariableAssignmentData(String name) {
        this.leadingText = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String name) {
        this.id = name;
    }

    public String getLeadingText() {
        return leadingText;
    }

    @Override
    public String toString() {
        return this.getLeadingText();
    }

    /**
     * Repositories committed before the reader kept what follows a variable statement's last
     * declaration (its ";") hold an empty trailing text; the next commit of the statement fills it
     * in. The trailing text is not part of equals().
     */
    @Override
    public void adoptMetadataFrom(ArtifactData newer) {
        if (this.getTrailingComment().isEmpty() && newer instanceof VariableAssignmentData other)
            this.setTrailingComment(other.getTrailingComment());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        VariableAssignmentData other = (VariableAssignmentData) obj;
        if (id == null) {
            return other.id == null;
        } else return id.equals(other.id);
    }
}
