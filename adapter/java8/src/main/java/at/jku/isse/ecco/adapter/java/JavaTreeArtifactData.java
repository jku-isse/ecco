package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class JavaTreeArtifactData implements ArtifactData {

    //Append new elements at the END!
    public enum NodeType {
        AFTER,
        ANNOTATIONMEMBER,
        ANNOTATIONMEMBER_DEFAULT,
        ANNOTATION_TYPE_DECLARATION,
        ANONYMOUS_CLASS_DECLARATION,
        ASSIGNMENT,
        BEFORE,
        BLOCK,
        CATCH,
        CATCH_META,
        CLASS_INSTANCE_CREATION,
        CONDITION,
        DECLARATION_EXTENDS,
        DECLARATION_IMPLEMENTS,
        DIMENSION,
        ENUM_CONSTANTS,
        ENUM_DECLARATION,
        EXPRESION_POSTFIX,
        EXPRESSION_CAST,
        EXPRESSION_PARENTHESIS,
        EXPRESSION_PREFIX,
        EXPRESSION_TRENARY,
        EXPRESSION_VARIABLE_DECLARATION,
        FIELD_DECLARATION,
        FIELD_INIT,
        FIELD_TYPE,
        FINALLY,
        GENERIC_TYPE_INFO,
        LAMBDA,
        LAMBDA_PARAMETERS,
        LOOP_DO_WHILE,
        LOOP_ENHANCED_FOR,
        LOOP_FOR,
        LOOP_WHILE,
        METHOD_DECLARATION,
        METHOD_INVOCATION,
        MODIFIER,
        PARAMETERS,
        PARAMETER_POSITION,
        SIMPLE_JUST_A_STRING,
        STATEMENT_ASSERT,
        STATEMENT_ASSERT_MESSAGE,
        STATEMENT_ELSE,
        STATEMENT_EXPRESSION,
        STATEMENT_IF,
        STATEMENT_RETURN,
        STATEMENT_VARIABLE_DECLARATION,
        SWITCH_CASE,
        SWITCH_SWITCH,
        SYNCHRONIZED_STATEMENT,
        THROWS_LIST,
        THROW_STATEMENT, TRY_META,
        TRY_RESSOURCES,
        TYPE_DECLARATION,
        VARIABLE_DECLARATION_FRAGMENT
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
                ", dataAsString='" + dataAsString + '\'' +
                '}';
    }
}
