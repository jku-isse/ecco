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
    }
}
