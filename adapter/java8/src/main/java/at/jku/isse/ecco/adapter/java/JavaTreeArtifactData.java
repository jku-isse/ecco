package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class JavaTreeArtifactData implements ArtifactData {

    public enum NodeType {
        ASSIGNMENT,
        ENUM_DECLARATION,
        TYPE_DECLARATION,
        ANNOTATIONTYPE_DECLARATION,
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
        EXPRESSION_CAST,
        EXPRESSION_TRENARY,
        EXPRESSION_PARENTHESIS,
        LOOP_FOR,
        LOOP_ENHANCED_FOR,
        FOR_INITALIZER,
        CONDITION,
        FOR_UPDATERS,
        FIELD_DECLARATION,
        TRY_META,
        TRY_RESSOURCES,
        CATCH_META,
        CATCH,
        FINALLY,
        LOOP_WHILE,
        LOOP_DO_WHILE,
        DECLARATION_EXTENDS,
        DECLARATION_IMPLEMENTS,
        ENUM_CONSTANTS,
        SWITCH_SWITCH,
        SWITCH_CASE,
        SYNCHRONIZED_STATEMENT,
        STATEMENT_ASSERT,
        STATEMENT_ASSERT_MESSAGE,
        STATEMENT_ASSERT_CONDITION,
        ANNOTATIONMEMBER,
        ANNOTATIONMEMBER_DEFAULT,
        LAMBDA,
        LAMBDA_PARAMETERS,
        MODIFIER,
        STATEMENT_RETURN,
        THROW_STATEMENT,
        GENERIC_TYPE_INFO,
        DIMENSION
    }

    private NodeType type;

    private transient boolean ordered;
    private String dataAsString;

    public String getDataAsString() {
        return dataAsString;
    }

    public void setDataAsString(String dataAsString) {
        this.dataAsString = dataAsString;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaTreeArtifactData that = (JavaTreeArtifactData) o;
        return type == that.type &&
                Objects.equals(dataAsString, that.dataAsString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, dataAsString);
    }

    @Override
    public String toString() {
        return "JavaTreeArtifactData{" +
                "type=" + type +
                ", ordered=" + ordered +
                ", dataAsString='" + dataAsString + '\'' +
                '}';
    }
}
