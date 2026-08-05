package at.jku.isse.ecco.adapter.typescript.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Most adapter-typescript data classes follow the same shape already covered for
 * adapter-cpp/adapter-text/adapter-file/adapter-image: a single String field, a constructor that
 * sets it, and content-based equals()/hashCode()/toString(). A shared helper verifies that common
 * contract instead of repeating it per class. A few classes only expose their field through
 * toString() (no typed getter) - a second, narrower helper covers those.
 */
public class ArtifactDataTest {

    private void assertStandardContract(Function<String, ArtifactData> factory, Function<ArtifactData, String> getter) {
        ArtifactData a = factory.apply("value");
        ArtifactData b = factory.apply("value");
        ArtifactData c = factory.apply("different");

        assertEquals("value", getter.apply(a));
        assertEquals("value", a.toString());

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(null, a);
        assertNotEquals("not the right type", a);
    }

    /** Same as assertStandardContract, but for classes that only expose their field via toString(). */
    private void assertToStringOnlyContract(Function<String, ArtifactData> factory) {
        ArtifactData a = factory.apply("value");
        ArtifactData b = factory.apply("value");
        ArtifactData c = factory.apply("different");

        assertEquals("value", a.toString());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    public void arrowFunctionArtifactData() {
        assertToStringOnlyContract(ArrowFunctionArtifactData::new);
    }

    @Test
    public void blockArtifactData() {
        assertToStringOnlyContract(BlockArtifactData::new);
    }

    @Test
    public void enumArtifactData() {
        assertToStringOnlyContract(EnumArtifactData::new);
    }

    @Test
    public void loopArtifactData() {
        assertToStringOnlyContract(LoopArtifactData::new);
    }

    @Test
    public void caseClauseArtifactData() {
        assertStandardContract(CaseClauseArtifactData::new, d -> ((CaseClauseArtifactData) d).getCaseClause());
    }

    @Test
    public void classArtifactData() {
        assertStandardContract(ClassArtifactData::new, d -> ((ClassArtifactData) d).getClassDecl());
    }

    @Test
    public void doBlockArtifactData() {
        assertStandardContract(DoBlockArtifactData::new, d -> ((DoBlockArtifactData) d).getDoBlock());
    }

    @Test
    public void fieldArtifactData() {
        assertStandardContract(FieldArtifactData::new, d -> ((FieldArtifactData) d).getField());
    }

    @Test
    public void functionArtifactData() {
        assertStandardContract(FunctionArtifactData::new, d -> ((FunctionArtifactData) d).getSignature());
    }

    @Test
    public void ifBlockArtifactData() {
        assertStandardContract(IfBlockArtifactData::new, d -> ((IfBlockArtifactData) d).getBlock());
    }

    @Test
    public void includeArtifactData() {
        assertStandardContract(IncludeArtifactData::new, d -> ((IncludeArtifactData) d).getImportName());
    }

    @Test
    public void leafArtifactData() {
        assertStandardContract(LeafArtifactData::new, d -> ((LeafArtifactData) d).getLine());
    }

    @Test
    public void switchBlockArtifactData() {
        assertStandardContract(SwitchBlockArtifactData::new, d -> ((SwitchBlockArtifactData) d).getSwitchBlock());
    }

    @Test
    public void whileBlockArtifactData() {
        assertStandardContract(WhileBlockArtifactData::new, d -> ((WhileBlockArtifactData) d).getWhileBlock());
    }

    @Test
    public void abstractArtifactDataHasIndependentOptionalCommentFields() {
        // AbstractArtifactData itself defines no equals()/hashCode()/toString() (subclasses that
        // extend it override all three based on their own field, never touching these) - it's purely
        // a grab-bag of optional leading/trailing comment text, so this only verifies the plain
        // getter/setter contract, using Object identity for equality (the default).
        AbstractArtifactData data = new AbstractArtifactData();
        assertEquals("", data.getLeadingComment());
        assertEquals("", data.getLeadingText());
        assertEquals("", data.getTrailingComment());

        data.setLeadingComment("// leading");
        data.setLeadingText("const x = 1;");
        data.setTrailingComment("// trailing");
        assertEquals("// leading", data.getLeadingComment());
        assertEquals("const x = 1;", data.getLeadingText());
        assertEquals("// trailing", data.getTrailingComment());
    }

    /**
     * Characterizes a real inconsistency, not a guess: the constructor parameter is assigned to
     * {@code leadingText} (and toString() returns leadingText), but equals()/hashCode() are based on
     * a completely different field, {@code id} - which the constructor never sets, so it stays null
     * unless setId() is called separately. Two instances built with different constructor arguments
     * (and therefore different toString()s) are still equal() to each other as long as neither ever
     * had setId() called - almost certainly not the intended behavior, but not fixed here since it's
     * out of scope for a test-only pass; flagging it is the point of this test.
     */
    @Test
    public void variableAssignmentDataEqualityIsBasedOnIdNotOnTheConstructorArgument() {
        VariableAssignmentData a = new VariableAssignmentData("x = 1");
        VariableAssignmentData b = new VariableAssignmentData("y = 2");

        assertEquals("x = 1", a.toString());
        assertEquals("y = 2", b.toString());
        assertNotEquals(a.toString(), b.toString(), "these were constructed with different text");

        assertEquals(a, b, "but they are equal(), because equals()/hashCode() use the never-set id field, not leadingText");

        a.setId("a");
        b.setId("b");
        assertNotEquals(a, b, "once id actually differs, equals() correctly picks that up");
    }
}
