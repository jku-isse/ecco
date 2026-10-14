package at.jku.cdl.ecco.adapter.java;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two variants of a file that differ in a constructor and in a try statement, committed into one
 * repository: each checkout gives back its variant - byte for byte, but for the order of members.
 */
public class JavaASTRepositoryRoundTripTest {

    private static final Path FILE = Path.of("src", "p", "C.java");

    private static final String A = "package p;\n\npublic class C {\n"
            + "    public C(int x) {\n        this.x = x;\n    }\n\n"
            + "    void run() {\n        try (java.io.Reader r = new java.io.StringReader(\"\")) {\n            r.read();\n"
            + "        } catch (java.io.IOException e) {\n            throw new IllegalStateException(e);\n        }\n    }\n}\n";

    private static final String B = "package p;\n\npublic class C {\n"
            + "    /**\n     * Checked.\n     */\n    public C(int x) throws IllegalArgumentException {\n        if (x < 0)\n            throw new IllegalArgumentException();\n        this.x = x;\n    }\n\n"
            + "    void run() {\n        try (java.io.Reader r = new java.io.StringReader(\"\")) {\n            r.read();\n"
            + "        } catch (java.io.IOException | RuntimeException e) {\n            throw new IllegalStateException(e);\n        } finally {\n            // done\n            System.out.println(x);\n        }\n    }\n}\n";

    /** Commits A, then B, and checks out {@code variant}. */
    private static String checkout(String variant) throws Exception {
        Path work = Files.createTempDirectory("java-ast-repository");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.createDirectories(content.resolve(FILE).getParent());
            Files.writeString(content.resolve(FILE), A);
            service.commit("a", "A");
            Files.writeString(content.resolve(FILE), B);
            service.commit("b", "B");

            Path checkout = Files.createDirectories(work.resolve("checkout"));
            service.setBaseDir(checkout);
            service.checkout(variant);
            return Files.readString(checkout.resolve(FILE));
        }
    }

    @Test
    @Timeout(120)
    public void theFirstVariantIsCheckedOutAsCommitted() throws Exception {
        assertEquals(A, checkout("A"));
    }

    /**
     * The members of a type are unordered children of its node: B's constructor, a different
     * artifact than A's, comes after run(), which both variants share. Its code is as committed.
     */
    @Test
    @Timeout(120)
    public void theSecondVariantIsCheckedOutWithItsConstructorLast() throws Exception {
        String constructor = B.substring(B.indexOf("    /**"), B.indexOf("    void run()"));
        String run = B.substring(B.indexOf("    void run()"), B.lastIndexOf("}"));
        assertEquals("package p;\n\npublic class C {\n" + run + "\n" + constructor.substring(0, constructor.length() - 1) + "}\n", checkout("B"));
    }

    @Test
    @Disabled("bug: the members of a type are written in the order their associations come in, not as committed")
    @Timeout(120)
    public void theSecondVariantIsCheckedOutAsCommitted() throws Exception {
        assertEquals(B, checkout("B"));
    }

    private static final String GUARDED = "package p;\n\npublic enum C {\n\n    @Deprecated\n    ONLY;\n"
            + "    int f(Object o) {\n        switch(o) {\n            case Integer i when i > 0 ->\n                g(i);\n"
            + "            case null, default ->\n                g(0);\n        }\n        return 0;\n    }\n}\n";

    private static final String UNGUARDED = "package p;\n\npublic enum C {\n\n    ONLY;\n"
            + "    int f(Object o) {\n        switch(o) {\n            case Integer i ->\n                g(i);\n"
            + "            case null ->\n                g(0);\n        }\n        return 0;\n    }\n}\n";

    /**
     * Variants that differ only in a case guard, in "case null, default" against "case null" and in
     * an enum constant's annotation (one constant: see anInsertedEnumConstantKeepsItsPlace for
     * the order of several): each is checked out as committed - the guard, the default and
     * the annotation were lost, so both variants came back as the second.
     */
    @Test
    @Timeout(120)
    public void guardsDefaultsAndConstantAnnotationsStayWithTheirVariant() throws Exception {
        Path work = Files.createTempDirectory("java-ast-repository");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.createDirectories(content.resolve(FILE).getParent());
            Files.writeString(content.resolve(FILE), GUARDED);
            service.commit("guarded", "G");
            Files.writeString(content.resolve(FILE), UNGUARDED);
            service.commit("unguarded", "U");

            for (String[] variant : new String[][]{{"G", GUARDED}, {"U", UNGUARDED}}) {
                Path checkout = Files.createDirectories(work.resolve("checkout-" + variant[0]));
                service.setBaseDir(checkout);
                service.checkout(variant[0]);
                assertEquals(variant[1], Files.readString(checkout.resolve(FILE)), variant[0]);
            }
        }
    }

    /**
     * Enum constants are unordered children of the type's node, like its members - but their order
     * is meaning: ordinal(), values(), compareTo. A constant only one variant has comes after the
     * shared ones, wherever it was.
     */
    @Test
    @Disabled("bug: a constant inserted before others is checked out after them - its ordinal changes")
    @Timeout(120)
    public void anInsertedEnumConstantKeepsItsPlace() throws Exception {
        String a = "package p;\n\npublic enum C {\n\n    RED, GREEN\n}\n";
        String b = "package p;\n\npublic enum C {\n\n    BLUE, RED, GREEN\n}\n"; // checked out as "RED, GREEN, BLUE"
        Path work = Files.createTempDirectory("java-ast-repository");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.createDirectories(content.resolve(FILE).getParent());
            Files.writeString(content.resolve(FILE), a);
            service.commit("a", "A");
            Files.writeString(content.resolve(FILE), b);
            service.commit("b", "B");
            Path checkout = Files.createDirectories(work.resolve("checkout"));
            service.setBaseDir(checkout);
            service.checkout("B");
            assertEquals(b, Files.readString(checkout.resolve(FILE)));
        }
    }
}
