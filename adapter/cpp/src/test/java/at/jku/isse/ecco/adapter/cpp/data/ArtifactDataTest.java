package at.jku.isse.ecco.adapter.cpp.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The adapter-cpp data classes are all the same shape: a single String field, a constructor that
 * sets it, a getter, and content-based equals()/hashCode()/toString() built from it - the same
 * boilerplate pattern already covered for adapter-text/adapter-file/adapter-image's ArtifactData
 * classes elsewhere in this test suite. One shared helper verifies the common contract for each,
 * rather than 13 nearly-identical hand-written test methods.
 */
public class ArtifactDataTest {

    /** Verifies the standard single-field contract: getter, toString(), equals()/hashCode() by content. */
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

        // equals() itself, not only through assertEquals' shortcuts
        assertTrue(a.equals(a));
        assertFalse(a.equals(null));
        assertFalse(a.equals("value"), "a String with the same text is not the artifact");
        assertFalse(a.equals(new SourceLineArtifactData("value")), "nor is a line of the current format");

        // a null field (the classes allow it) equals only another null field
        ArtifactData none = factory.apply(null);
        assertEquals(none, factory.apply(null));
        assertEquals(none.hashCode(), factory.apply(null).hashCode());
        assertNotEquals(none, a);
        assertNotEquals(a, none);

        // the first format's artifacts mark a repository as retired (see RetiredFormat)
        assertNotNull(a.retiredFormat());
    }

    @Test
    public void abstractArtifactData() {
        assertStandardContract(AbstractArtifactData::new, d -> ((AbstractArtifactData) d).getId());
    }

    @Test
    public void blockArtifactData() {
        assertStandardContract(BlockArtifactData::new, d -> ((BlockArtifactData) d).getBlock());
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
    public void forBlockArtifactData() {
        assertStandardContract(ForBlockArtifactData::new, d -> ((ForBlockArtifactData) d).getForBlock());
    }

    @Test
    public void functionArtifactData() {
        assertStandardContract(FunctionArtifactData::new, d -> ((FunctionArtifactData) d).getSignature());
    }

    @Test
    public void ifBlockArtifactData() {
        assertStandardContract(IfBlockArtifactData::new, d -> ((IfBlockArtifactData) d).getIfBlock());
    }

    @Test
    public void includeArtifactData() {
        assertStandardContract(IncludeArtifactData::new, d -> ((IncludeArtifactData) d).getImportName());
    }

    @Test
    public void lineArtifactData() {
        assertStandardContract(LineArtifactData::new, d -> ((LineArtifactData) d).getLine());
    }

    @Test
    public void problemBlockArtifactData() {
        assertStandardContract(ProblemBlockArtifactData::new, d -> ((ProblemBlockArtifactData) d).getProblemBlock());
    }

    @Test
    public void switchBlockArtifactData() {
        assertStandardContract(SwitchBlockArtifactData::new, d -> ((SwitchBlockArtifactData) d).getSwitchBlock());
    }

    @Test
    public void whileBlockArtifactData() {
        assertStandardContract(WhileBlockArtifactData::new, d -> ((WhileBlockArtifactData) d).getWhileBlock());
    }

    /** CaseBlockArtifactData has a second, unrelated field (sameline) not involved in equals()/hashCode(). */
    @Test
    public void caseBlockArtifactData() {
        assertStandardContract(CaseBlockArtifactData::new, d -> ((CaseBlockArtifactData) d).getCaseblock());

        CaseBlockArtifactData data = new CaseBlockArtifactData("case 1:");
        assertNull(data.getSameline(), "sameline is not set by the constructor");
        data.setSameline(true);
        assertTrue(data.getSameline());

        CaseBlockArtifactData other = new CaseBlockArtifactData("case 1:");
        other.setSameline(false);
        assertEquals(data, other, "sameline must not affect equality, only caseblock does");
        assertEquals(data.hashCode(), other.hashCode());

        data.setCaseblock("case 2:");
        assertEquals("case 2:", data.getCaseblock());
        assertNotEquals(data, other);
    }

    /** the setters change the value compared: a stored artifact's data must not be changed afterwards */
    @Test
    public void settersChangeTheValueCompared() {
        BlockArtifactData block = new BlockArtifactData("{");
        block.setBlock("} else {");
        assertEquals(new BlockArtifactData("} else {"), block);
        AbstractArtifactData group = new AbstractArtifactData("INCLUDES");
        group.setId("DEFINES");
        assertEquals(new AbstractArtifactData("DEFINES"), group);
        DoBlockArtifactData doBlock = new DoBlockArtifactData("do {");
        doBlock.setDoBlock("do");
        assertEquals("do", doBlock.getDoBlock());
        FieldArtifactData field = new FieldArtifactData("int a;");
        field.setField("int b;");
        assertEquals("int b;", field.getField());
        ForBlockArtifactData forBlock = new ForBlockArtifactData("for (;;) {");
        forBlock.setForBlock("for (;;)");
        assertEquals("for (;;)", forBlock.getForBlock());
        FunctionArtifactData function = new FunctionArtifactData("int f()");
        function.setSignature("int g()");
        assertEquals("int g()", function.getSignature());
        IfBlockArtifactData ifBlock = new IfBlockArtifactData("if (a) {");
        ifBlock.setIfBlock("if (b) {");
        assertEquals("if (b) {", ifBlock.getIfBlock());
        IncludeArtifactData include = new IncludeArtifactData("#include <a>");
        include.setImportName("#include <b>");
        assertEquals("#include <b>", include.getImportName());
        ProblemBlockArtifactData problem = new ProblemBlockArtifactData("x");
        problem.setProblemBlock("y");
        assertEquals("y", problem.getProblemBlock());
        SwitchBlockArtifactData switchBlock = new SwitchBlockArtifactData("switch (a) {");
        switchBlock.setSwitchBlock("switch (b) {");
        assertEquals("switch (b) {", switchBlock.getSwitchBlock());
        WhileBlockArtifactData whileBlock = new WhileBlockArtifactData("while (a) {");
        whileBlock.setWhileBlock("while (b) {");
        assertEquals("while (b) {", whileBlock.getWhileBlock());
    }

    /**
     * A line of the current format is the file's line exactly as it is - the writer writes the stored
     * text - so every character counts: indentation, tabs, trailing blanks.
     */
    @Test
    public void sourceLineArtifactData() {
        SourceLineArtifactData line = new SourceLineArtifactData("    return 0;");
        assertTrue(line.equals(line));
        assertEquals(line, new SourceLineArtifactData("    return 0;"));
        assertEquals(line.hashCode(), new SourceLineArtifactData("    return 0;").hashCode());
        assertEquals("    return 0;", line.getLine());
        assertEquals("    return 0;", line.toString());
        assertNull(line.retiredFormat());
        assertFalse(line.equals(null));
        assertFalse(line.equals("    return 0;"));
        assertFalse(line.equals(new LineArtifactData("    return 0;")), "the first format's line is another artifact");
        assertNotEquals(line, new SourceLineArtifactData("\treturn 0;"), "indentation");
        assertNotEquals(line, new SourceLineArtifactData("    return 0; "), "trailing blank");
        assertNotEquals(line, new SourceLineArtifactData("    return  0;"), "inner blank");
        assertNotEquals(new SourceLineArtifactData(""), new SourceLineArtifactData(" "), "empty and blank line");
        assertThrows(NullPointerException.class, () -> new SourceLineArtifactData(null));
    }

    /**
     * A scope is identified by its kind and its (whitespace-normalized, see CppReader) signature:
     * a function and an enum of the same name differ, and so do two functions whose parameters
     * differ. It writes no text, so nothing else counts.
     */
    @Test
    public void scopeArtifactData() {
        ScopeArtifactData f = new ScopeArtifactData(ScopeArtifactData.Kind.FUNCTION, "int f(int a)");
        assertTrue(f.equals(f));
        assertEquals(f, new ScopeArtifactData(ScopeArtifactData.Kind.FUNCTION, "int f(int a)"));
        assertEquals(f.hashCode(), new ScopeArtifactData(ScopeArtifactData.Kind.FUNCTION, "int f(int a)").hashCode());
        assertEquals(ScopeArtifactData.Kind.FUNCTION, f.getKind());
        assertEquals("int f(int a)", f.getSignature());
        assertNull(f.retiredFormat());
        assertNotEquals(f, new ScopeArtifactData(ScopeArtifactData.Kind.FUNCTION, "int f(long a)"), "signature");
        assertNotEquals(new ScopeArtifactData(ScopeArtifactData.Kind.NAMESPACE, "geo"), new ScopeArtifactData(ScopeArtifactData.Kind.ENUM, "geo"), "kind");
        assertFalse(f.equals(null));
        assertFalse(f.equals("function int f(int a)"));
        assertFalse(f.equals(new SourceLineArtifactData("int f(int a)")));
        assertThrows(NullPointerException.class, () -> new ScopeArtifactData(null, "x"));
        assertThrows(NullPointerException.class, () -> new ScopeArtifactData(ScopeArtifactData.Kind.CLASS, null));

        assertEquals("namespace geo", new ScopeArtifactData(ScopeArtifactData.Kind.NAMESPACE, "geo").toString());
        assertEquals("class Shape", new ScopeArtifactData(ScopeArtifactData.Kind.CLASS, "class Shape").toString());
        assertEquals("extern \"C\"", new ScopeArtifactData(ScopeArtifactData.Kind.LINKAGE, "\"C\"").toString());
        assertEquals("enum Kind", new ScopeArtifactData(ScopeArtifactData.Kind.ENUM, "Kind").toString());
        assertEquals("function int f(int a)", f.toString());
    }
}
