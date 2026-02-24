package at.jku.isse.ecco.adapter.typescript.data;

import java.util.Objects;

public class CaseClauseArtifactData extends AbstractArtifactData {

    private final String caseClause;


    public CaseClauseArtifactData(String caseClause) {
        this.caseClause = caseClause;
    }


    public String getCaseClause() {
        return this.caseClause;
    }


    @Override
    public String toString() {
        return this.caseClause;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.caseClause);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CaseClauseArtifactData other = (CaseClauseArtifactData) obj;
        if (caseClause == null) {
            return other.caseClause == null;
        } else return caseClause.equals(other.caseClause);
    }

}
