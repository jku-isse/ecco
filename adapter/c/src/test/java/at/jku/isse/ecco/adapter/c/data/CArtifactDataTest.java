package at.jku.isse.ecco.adapter.c.data;

import at.jku.isse.ecco.adapter.c.CReader;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ECCO matches artifacts across variants by the equals/hashCode of their data: two lines that
 * differ but compare equal would let one variant's line replace the other's at checkout, two equal
 * ones that compare unequal would be duplicated. The C adapter writes a line's text exactly as it
 * is stored, so all of it counts - indentation, tabs and trailing blanks included (a difference in
 * them is a difference in the file). A function is identified by its signature: the parser's tokens
 * of its declaration specifiers and declarator, without whitespace or comments, so reformatting a
 * declaration keeps the function, while its lines (signature line included) are compared exactly.
 */
public class CArtifactDataTest {

    private static void assertContract(Function<String, ArtifactData> factory) {
        ArtifactData a = factory.apply("int x;");
        assertEquals(a, a);
        assertEquals(a, factory.apply("int x;"));
        assertEquals(a.hashCode(), factory.apply("int x;").hashCode());
        assertNotEquals(a, factory.apply("int y;"));
        assertFalse(a.equals(null));
        assertFalse(a.equals("int x;"), "a String with the same text is not the artifact");

        // a null text equals only another null text
        ArtifactData none = factory.apply(null);
        assertEquals(none, factory.apply(null));
        assertEquals(none.hashCode(), factory.apply(null).hashCode());
        assertNotEquals(none, a);
        assertNotEquals(a, none);
    }

    @Test
    public void lineContract() {
        assertContract(LineArtifactData::new);
        assertEquals("int x;", new LineArtifactData("int x;").getLine());
        assertEquals("int x;", new LineArtifactData("int x;").toString());
    }

    @Test
    public void functionContract() {
        assertContract(FunctionArtifactData::new);
        assertEquals("intf()", new FunctionArtifactData("intf()").getSignature());
        assertEquals("intf()", new FunctionArtifactData("intf()").toString());
    }

    @Test
    public void lineAndFunctionWithTheSameTextDiffer() {
        assertNotEquals(new LineArtifactData("intf()"), new FunctionArtifactData("intf()"));
        assertNotEquals(new FunctionArtifactData("intf()"), new LineArtifactData("intf()"));
    }

    /** every character of a line counts: the writer writes exactly the stored text */
    @Test
    public void linesDifferingOnlyInWhitespaceDiffer() {
        LineArtifactData line = new LineArtifactData("    return 0;");
        assertNotEquals(line, new LineArtifactData("\treturn 0;"), "indentation");
        assertNotEquals(line, new LineArtifactData("    return 0; "), "trailing blank");
        assertNotEquals(line, new LineArtifactData("    return  0;"), "inner blank");
        assertNotEquals(new LineArtifactData(""), new LineArtifactData(" "), "empty and blank line");
    }

    /** the signature comes from the parser's tokens: layout and comments do not change it */
    @Test
    public void aFunctionSignatureIgnoresLayoutButNotTypes(@TempDir Path dir) throws IOException {
        List<String> signatures = signatures(dir, """
                int f(int a, char **b) {
                    return a;
                }
                int
                f( int  a ,char**b /* argv */ ) {
                    return a;
                }
                int f(long a, char **b) {
                    return a;
                }
                static int f(int a, char **b) {
                    return a;
                }
                """);
        assertEquals(List.of("intf(inta,char**b)", "intf(inta,char**b)", "intf(longa,char**b)", "staticintf(inta,char**b)"), signatures);
        assertEquals(new FunctionArtifactData(signatures.get(0)), new FunctionArtifactData(signatures.get(1)));
        assertNotEquals(new FunctionArtifactData(signatures.get(0)), new FunctionArtifactData(signatures.get(2)));
        assertNotEquals(new FunctionArtifactData(signatures.get(0)), new FunctionArtifactData(signatures.get(3)));
    }

    /**
     * Joining tokens without a separator makes some different declarations one signature: "long long
     * f()" and "long longf()" (a long named longf), and K&R parameter types are not part of it. Such
     * functions are aligned as one function; their lines still differ, so no code is lost - only the
     * grouping is shared (see CRepositoryRoundTripTest#aFunctionSignature for the usual case).
     */
    @Test
    public void tokensJoinedWithoutSeparatorCanCollide(@TempDir Path dir) throws IOException {
        List<String> signatures = signatures(dir, """
                long long f() {
                    return 1;
                }
                long longf() {
                    return 2;
                }
                int k(a)
                    int a;
                {
                    return a;
                }
                int k(a)
                    long a;
                {
                    return a;
                }
                """);
        assertEquals(List.of("longlongf()", "longlongf()", "intk(a)", "intk(a)"), signatures);
    }

    private static List<String> signatures(Path dir, String source) throws IOException {
        Files.writeString(dir.resolve("f.c"), source);
        Node file = new CReader(new SerEntityFactory()).read(dir, new Path[]{Path.of("f.c")}).iterator().next();
        List<String> signatures = new ArrayList<>();
        for (Node child : file.getChildren())
            if (child.getArtifact().getData() instanceof FunctionArtifactData function)
                signatures.add(function.getSignature());
        return signatures;
    }
}
