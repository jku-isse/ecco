package at.jku.isse.ecco.plugin.artifact.java.eight;

import at.jku.isse.ecco.artifact.ArtifactData;

public class JavaTreeArtifactData implements ArtifactData {

    public enum NodeType {
        ASSIGNMENT,
        TYPE_DECLARATION,
        SIMPLE_JUST_A_STRING,
        METHOD_DECLARATION,
        METHOD_INVOCATION,
        THROWS_LIST,
        BLOCK,
        STATEMENT_EXPRESSION,
        STATEMENT_VARIABLE_DECLARATION,
        EXPRESSION_VARIABLE_DECLARATION,
        VARIABLE_DECLARATION_FRAGMENT,
        CLASS_INSTANCE_CREATION,
        PARAMETERS,
        ANONYMOUS_CLASS_DECLARATION,
        BEFORE,
        AFTER,
        STATEMENT_IF,
        STATEMENT_IFFS,
        STATEMENT_ELSE,
        EXPRESSION_PREFIX,
        EXPRESION_POSTFIX,
        LOOP_FOR,
        LOOP_ENHANCED_FOR,
        FOR_INITALIZER,
        FOR_CONDITION,
        FOR_UPDATERS,
        FIELD_DECLARATION,
        TRY_META,
        TRY_RESSOURCES,
        CATCH_META,
        CATCH,
        FINALLY,
        LOOP_WHILE,
        LOOP_DO_WHILE
    }

    private String codeBeforeChildren, codeAfterChildren;
    private boolean ordered;
    private NodeType type;
    private String identifierString;

    public String getCodeBeforeChildren() {
        return codeBeforeChildren;
    }

    public void setCodeBeforeChildren(String codeBeforeChildren) {
        this.codeBeforeChildren = codeBeforeChildren;
    }

    public String getCodeAfterChildren() {
        return codeAfterChildren;
    }

    public void setCodeAfterChildren(String codeAfterChildren) {
        this.codeAfterChildren = codeAfterChildren;
    }

    public boolean isOrdered() {
        return ordered;
    }

    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getIdentifierString() {
        return identifierString;
    }

    public void setIdentifierString(String identifierString) {
        this.identifierString = identifierString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JavaTreeArtifactData that = (JavaTreeArtifactData) o;

        if (ordered != that.ordered) return false;
        if (codeBeforeChildren != null ? !codeBeforeChildren.equals(that.codeBeforeChildren) : that.codeBeforeChildren != null)
            return false;
        if (codeAfterChildren != null ? !codeAfterChildren.equals(that.codeAfterChildren) : that.codeAfterChildren != null)
            return false;
        if (type != that.type) return false;
        return identifierString != null ? identifierString.equals(that.identifierString) : that.identifierString == null;
    }

    @Override
    public int hashCode() {
        int result = codeBeforeChildren != null ? codeBeforeChildren.hashCode() : 0;
        result = 31 * result + (codeAfterChildren != null ? codeAfterChildren.hashCode() : 0);
        result = 31 * result + (ordered ? 1 : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (identifierString != null ? identifierString.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "JavaTreeArtifactData{" +
                "codeBeforeChildren='" + codeBeforeChildren + '\'' +
                ", codeAfterChildren='" + codeAfterChildren + '\'' +
                ", ordered=" + ordered +
                ", type=" + type +
                ", identifierString='" + identifierString + '\'' +
                '}';
    }
}
