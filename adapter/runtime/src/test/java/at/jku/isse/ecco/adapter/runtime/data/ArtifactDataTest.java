package at.jku.isse.ecco.adapter.runtime.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Most adapter-runtime data classes follow the single-String-field pattern already covered for
 * adapter-cpp/adapter-typescript/adapter-text/adapter-file/adapter-image elsewhere in this test
 * suite. AbstractArtifactData is different enough (it's actually live - see RuntimeReader.java's
 * "INCLUDES"/"METHODS"/... group nodes) to need its own, more careful tests: real quirks found by
 * reading it, then confirmed here rather than assumed.
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

    @Test
    public void blockArtifactData() {
        assertStandardContract(BlockArtifactData::new, d -> ((BlockArtifactData) d).getBlock());
    }

    @Test
    public void fieldArtifactData() {
        assertStandardContract(FieldArtifactData::new, d -> ((FieldArtifactData) d).getField());
    }

    @Test
    public void importArtifactData() {
        assertStandardContract(ImportArtifactData::new, d -> ((ImportArtifactData) d).getImportName());
    }

    @Test
    public void lineArtifactData() {
        assertStandardContract(LineArtifactData::new, d -> ((LineArtifactData) d).getLine());
    }

    @Test
    public void methodArtifactData() {
        assertStandardContract(MethodArtifactData::new, d -> ((MethodArtifactData) d).getSignature());
    }

    /** ClassArtifactData has 4 more optional fields, none of which participate in equals()/hashCode()/toString(). */
    @Test
    public void classArtifactData() {
        assertStandardContract(ClassArtifactData::new, d -> ((ClassArtifactData) d).getName());

        ClassArtifactData full = new ClassArtifactData("Foo", "public class Foo {", "@Deprecated", "// a class", "/** javadoc */");
        assertEquals("Foo", full.getName());
        assertEquals("public class Foo {", full.getClassDeclaration());
        assertEquals("@Deprecated", full.getAnnotation());
        assertEquals("// a class", full.getComment());
        assertEquals("/** javadoc */", full.getJavadoc());

        ClassArtifactData sameNameDifferentEverythingElse = new ClassArtifactData("Foo", "different decl", "different annotation", "different comment", "different javadoc");
        assertEquals(full, sameNameDifferentEverythingElse, "only name participates in equality");
    }

    @Test
    public void abstractArtifactDataExecutedFlagDefaultsFalseAndCanOnlyBeSetTrue() {
        AbstractArtifactData data = new AbstractArtifactData("id");

        assertFalse(data.isExecuted());
        data.setExecuted();
        assertTrue(data.isExecuted());
    }

    /**
     * source (backing getFile()) has a getter but no setter anywhere in the class, and is never
     * assigned in the constructor either - there is no way to make it anything other than null, so
     * getFile() is dead code that can only ever return "". Characterizing this as-is rather than
     * guessing there might be a way to set it.
     */
    @Test
    public void abstractArtifactDataGetFileAlwaysReturnsEmptyStringSinceSourceCanNeverBeSet() {
        AbstractArtifactData data = new AbstractArtifactData("id");

        assertEquals("", data.getFile());
    }

    @Test
    public void abstractArtifactDataEqualsStripsTabsThenTrimsBeforeComparing() {
        AbstractArtifactData a = new AbstractArtifactData("some\ttext");
        AbstractArtifactData b = new AbstractArtifactData(" sometext ");

        // a's tabs are stripped entirely (not replaced with a space) leaving "sometext", which then
        // matches b's already-tab-free "sometext" once surrounding whitespace is trimmed from both.
        assertEquals(a, b);
    }

    /**
     * Real bug found by reading the code, confirmed here rather than assumed: equals() calls
     * {@code id.contains("\t")} and {@code other.id.contains("\t")} BEFORE its own null check
     * ({@code if (id == null) ...}), so that null check is unreachable dead code - a null id crashes
     * with NPE instead of comparing false. Not reachable through RuntimeReader's own usage (it always
     * constructs AbstractArtifactData with a non-null literal like "METHODS"), so this is latent
     * rather than an active production bug, but worth pinning down since it would surface immediately
     * if AbstractArtifactData("...") were ever called with a null argument. Not fixed here - a real
     * fix means deciding what null should mean for a group-marker node, not something to guess at in
     * a test-only pass.
     */
    @Test
    public void abstractArtifactDataEqualsThrowsForANullId() {
        AbstractArtifactData withNullId = new AbstractArtifactData(null);
        AbstractArtifactData other = new AbstractArtifactData("value");

        assertThrows(NullPointerException.class, () -> withNullId.equals(other));
    }
}
