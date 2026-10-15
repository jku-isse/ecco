package at.jku.isse.ecco.adapter.cpp;

import at.jku.isse.ecco.adapter.cpp.data.*;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repositories committed with the first C++ adapter can still be checked out (NFR-V2): CppWriter hands
 * their trees to LegacyCppWriter. Nothing else exercised it. This pins down what it writes for a
 * tree shaped like the ones the first reader built (see CppReader before d9ce686c): the INCLUDES,
 * DEFINES and FIELDS groups first, then the functions with their statement blocks - the order and
 * the loss of comments are that format's, not something to fix here.
 */
public class LegacyCppWriterTest {

    private final EntityFactory entities = new SerEntityFactory();

    @Test
    public void aTreeInTheFirstFormatIsWrittenAsThatFormatDid(@TempDir Path out) throws IOException {
        Node.Op file = this.node(new PluginArtifactData(CppPlugin.class.getName(), Path.of("legacy.cpp")));

        Node.Op includes = this.child(file, new AbstractArtifactData("INCLUDES"));
        this.child(includes, new IncludeArtifactData("#include <stdio.h>"));
        this.child(includes, new IncludeArtifactData("#include \"util.h\""));
        this.child(this.child(file, new AbstractArtifactData("DEFINES")), new LineArtifactData("#define MAX 10"));
        this.child(this.child(file, new AbstractArtifactData("FIELDS")), new FieldArtifactData("int counter = 0;"));

        Node.Op functions = this.child(file, new AbstractArtifactData("FUNCTIONS"));
        Node.Op main = this.child(functions, new FunctionArtifactData("int main(int argc)"));
        this.lines(this.child(main, new IfBlockArtifactData("if (argc > 1) {")), "    counter++;", "}");
        this.lines(this.child(main, new ForBlockArtifactData("for (int i = 0; i < MAX; i++) {")), "    counter += i;", "}");
        this.lines(this.child(main, new WhileBlockArtifactData("while (counter > 0) {")), "    counter--;", "}");
        this.lines(this.child(main, new DoBlockArtifactData("do {")), "    tick();", "} while (0);");
        Node.Op switchBlock = this.child(main, new SwitchBlockArtifactData("switch (argc) {"));
        CaseBlockArtifactData sameLine = new CaseBlockArtifactData("case 1:");
        sameLine.setSameline(true);
        this.lines(this.child(switchBlock, sameLine), " return 1;");
        CaseBlockArtifactData ownLines = new CaseBlockArtifactData("default:");
        ownLines.setSameline(false);
        this.lines(this.child(switchBlock, ownLines), "    return 0;");
        this.lines(switchBlock, "}");
        this.child(main, new ProblemBlockArtifactData("UNPARSED(x)"));
        this.lines(this.child(main, new BlockArtifactData("{")), "}");
        this.lines(main, "return counter;", "}");

        Path[] written = new CppWriter().write(out, Set.of(file));

        assertArrayEquals(new Path[]{out.resolve("legacy.cpp")}, written);
        assertEquals("""
                #include <stdio.h>
                #include "util.h"

                #define MAX 10

                int counter = 0;


                int main(int argc)
                if (argc > 1) {
                    counter++;
                }
                for (int i = 0; i < MAX; i++) {
                    counter += i;
                }
                while (counter > 0) {
                    counter--;
                }
                do {
                    tick();
                } while (0);
                switch (argc) {
                case 1: return 1;
                default:
                    return 0;
                }
                UNPARSED(x)
                {
                }
                return counter;
                }
                """, Files.readString(written[0]));
    }

    @Test
    public void aFileWithoutContentIsNotWritten(@TempDir Path out) throws IOException {
        Node.Op file = this.node(new PluginArtifactData(CppPlugin.class.getName(), Path.of("empty.cpp")));
        assertNull(new LegacyCppWriter().processNode(file, out));
        assertFalse(Files.exists(out.resolve("empty.cpp")));
    }

    @Test
    public void artifactsOfAnotherKindAreLeftOutWithAWarning(@TempDir Path out) throws IOException {
        // e.g. a line of the current format inside a retired tree: logged, not written
        Node.Op file = this.node(new PluginArtifactData(CppPlugin.class.getName(), Path.of("mixed.cpp")));
        Node.Op functions = this.child(file, new AbstractArtifactData("FUNCTIONS"));
        this.child(functions, new LineArtifactData("int kept;"));
        this.child(functions, new SourceLineArtifactData("int left out;"));

        new CppWriter().write(out, Set.of(file));

        String content = Files.readString(out.resolve("mixed.cpp"));
        assertTrue(content.contains("int kept;"), content);
        assertFalse(content.contains("left out"), content);
    }

    @Test
    public void onlyFileNodesAreWritten(@TempDir Path out) throws IOException {
        assertNull(new LegacyCppWriter().processNode(this.node(new LineArtifactData("int x;")), out));
    }

    /** empty groups and blocks without children (e.g. a function whose body the first reader skipped) */
    @Test
    public void emptyGroupsAndBlocksAreWrittenAsTheirHeaders(@TempDir Path out) throws IOException {
        Node.Op file = this.node(new PluginArtifactData(CppPlugin.class.getName(), Path.of("empty-groups.cpp")));
        this.child(file, new AbstractArtifactData("INCLUDES"));
        this.child(file, new AbstractArtifactData("DEFINES"));
        this.child(file, new AbstractArtifactData("FIELDS"));
        Node.Op functions = this.child(file, new AbstractArtifactData("FUNCTIONS"));
        Node.Op main = this.child(functions, new FunctionArtifactData("int main()"));
        this.child(main, new IfBlockArtifactData("if (a)"));
        this.child(main, new ForBlockArtifactData("for (;;)"));
        this.child(main, new WhileBlockArtifactData("while (b)"));
        this.child(main, new DoBlockArtifactData("do"));
        this.child(main, new BlockArtifactData("{}"));
        Node.Op switchBlock = this.child(main, new SwitchBlockArtifactData("switch (c) {"));
        CaseBlockArtifactData sameLine = new CaseBlockArtifactData("case 1:");
        sameLine.setSameline(true);
        this.child(switchBlock, sameLine);
        CaseBlockArtifactData ownLines = new CaseBlockArtifactData("default:");
        ownLines.setSameline(false);
        this.child(switchBlock, ownLines);
        Node.Op problem = this.child(main, new ProblemBlockArtifactData("UNPARSED(x)"));
        this.lines(problem, "    inside the problem;");
        Node.Op line = this.child(main, new LineArtifactData("label:"));
        this.lines(line, "    under the line;");
        Node.Op withoutBody = this.child(functions, new FunctionArtifactData("void g()"));

        assertNotNull(withoutBody);
        new CppWriter().write(out, Set.of(file));

        assertEquals("""




                int main()
                if (a)
                for (;;)
                while (b)
                do
                {}
                switch (c) {
                case 1:
                default:
                UNPARSED(x)
                    inside the problem;
                label:
                    under the line;

                void g()
                """, Files.readString(out.resolve("empty-groups.cpp")));
    }

    /**
     * A case on the same line as its statement is written as one line: its children's own text
     * only - grandchildren are not visited. The first reader gave such a case only leaf lines (the
     * statement and "break;"), but CaseBlockArtifactData's identity ignores sameline, so a variant
     * whose same case had a block under it (sameline false) is aligned with it, and checking that
     * variant out writes the block's header without its body. Current behaviour (retired format,
     * checkout only).
     */
    @Test
    public void aSameLineCaseDropsWhatIsNestedInItsChildren(@TempDir Path out) throws IOException {
        Node.Op file = this.node(new PluginArtifactData(CppPlugin.class.getName(), Path.of("case.cpp")));
        Node.Op main = this.child(this.child(file, new AbstractArtifactData("FUNCTIONS")), new FunctionArtifactData("int main()"));
        Node.Op switchBlock = this.child(main, new SwitchBlockArtifactData("switch (c) {"));
        CaseBlockArtifactData sameLine = new CaseBlockArtifactData("case 1:");
        sameLine.setSameline(true);
        Node.Op caseNode = this.child(switchBlock, sameLine);
        this.lines(this.child(caseNode, new IfBlockArtifactData(" if (d) {")), "    run();", "}");
        this.lines(switchBlock, "}");

        new CppWriter().write(out, Set.of(file));

        assertEquals("\n\n\n\nint main()\nswitch (c) {\ncase 1: if (d) {\n}\n", Files.readString(out.resolve("case.cpp")));
    }

    /**
     * How the legacy writer is reached: a checkout of a repository committed with the first adapter.
     * resources/retired-format-repository/ecco is such a repository, made with the CLI of d9ce686c^ (its storage converted to the current layout with 4473766d)
     * (the last version with the first adapter): main.cpp committed as variant A, then as B (another
     * include, condition and loop). The expected files are what that CLI checked out - the format's
     * own reordering, re-indentation and loss of the comment included; the current version must
     * reproduce them exactly, and refuse to commit to the repository.
     */
    @Test
    @Timeout(120)
    public void aRepositoryOfTheFirstAdapterIsCheckedOutAsThatVersionDid(@TempDir Path work) throws Exception {
        Path repository = copyFixture("retired-format-repository", work);
        String common = "\n#define MAX 10\n\nint counter = 0;\n\n\n\nint main(int argc, char **argv) {\n\n";
        String switchAndReturn = "switch(argc){\n    case 1: return 1;\n    default:\n        counter--;\n        break;\n}\n    return counter;\n}\n";
        String a = "#include <cstdio>\n" + common
                + "    if (argc > 1) {\n        counter++;\n}\nfor(int i = 0; i < MAX; i++) {\n        counter += i;\n}\n" + switchAndReturn;
        String b = "#include <cstdio>\n#include <cstdlib>\n" + common
                + "    if (argc > 2) {\n        counter++;\n}\nwhile( counter > 0 )  {\n        counter--;\n}\n" + switchAndReturn;

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repository);
            service.open();
            for (String[] variant : new String[][]{{"A", a}, {"B", b}}) {
                Path out = Files.createDirectories(work.resolve("out-" + variant[0]));
                service.setBaseDir(out);
                service.checkout(variant[0]);
                assertEquals(variant[1], Files.readString(out.resolve("main.cpp")), variant[0]);
            }

            Path content = Files.createDirectories(work.resolve("content"));
            Files.writeString(content.resolve("main.cpp"), "int main() {\n    return 0;\n}\n");
            service.setBaseDir(content);
            EccoException refused = assertThrows(EccoException.class, () -> service.commit("c", "C"));
            String message = refused.getMessage() + " " + (refused.getCause() == null ? "" : refused.getCause().getMessage());
            assertTrue(message.contains("re-create the repository"), message);
        }
    }

    private static final String CASE_SAME_LINE = "int main(int argc, char **argv) {\n    int counter = 0;\n    switch (argc) {\n"
            + "    case 1: counter++; break;\n    default:\n        counter--;\n    }\n    return counter;\n}\n";
    private static final String CASE_WITH_BLOCK = "int main(int argc, char **argv) {\n    int counter = 0;\n    switch (argc) {\n"
            + "    case 1:\n        if (argc > 3) {\n            counter += 2;\n        }\n        break;\n    default:\n        counter--;\n    }\n    return counter;\n}\n";

    /**
     * resources/retired-format-repository-case, made like the one above: variant A has
     * {@value #CASE_SAME_LINE}, B the same case on its own line with a block under it
     * ({@value #CASE_WITH_BLOCK}). The case artifacts are equal - CaseBlockArtifactData ignores
     * sameline - so B's case is A's same-line case, written as one line of its children's text:
     * B's "counter += 2;" and the if's closing brace are lost. The first version's CLI checked B out
     * exactly like this; the legacy writer keeps doing so. Current behaviour, see the disabled test.
     */
    @Test
    @Timeout(120)
    public void aCaseOnItsOwnLineMergedWithASameLineCaseLosesItsBlock(@TempDir Path work) throws Exception {
        Path repository = copyFixture("retired-format-repository-case", work);
        String head = "\n\n\n\nint main(int argc, char **argv) {\n\n    int counter = 0;\nswitch(argc){\n";
        String tail = "    default:\n        counter--;\n}\n    return counter;\n}\n";
        assertEquals(head + "    case 1: counter++; break;\n" + tail, checkout(repository, work, "A"));
        assertEquals(head + "    case 1:        if (argc > 3) {        break;\n" + tail, checkout(repository, work, "B"));
    }

    @Test
    @Disabled("bug (retired format, inherited from the first adapter): a case merged with a same-line case loses what is nested in its statements")
    @Timeout(120)
    public void aCaseOnItsOwnLineKeepsItsBlock(@TempDir Path work) throws Exception {
        Path repository = copyFixture("retired-format-repository-case", work);
        String b = checkout(repository, work, "B");
        assertTrue(b.contains("counter += 2;"), b);
    }

    private static String checkout(Path repository, Path work, String configuration) throws IOException {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repository);
            service.open();
            Path out = Files.createDirectories(work.resolve("out-" + configuration));
            service.setBaseDir(out);
            service.checkout(configuration);
            return Files.readString(out.resolve("main.cpp"));
        }
    }

    /** a copy of a repository fixture, so that opening it cannot change the original */
    private static Path copyFixture(String name, Path work) throws Exception {
        Path repository = work.resolve(".ecco");
        Path fixture = Path.of(LegacyCppWriterTest.class.getResource("/" + name + "/ecco").toURI());
        try (Stream<Path> files = Files.walk(fixture)) {
            for (Path file : files.toList())
                Files.copy(file, repository.resolve(fixture.relativize(file).toString()));
        }
        return repository;
    }


    private Node.Op node(ArtifactData data) {
        return this.entities.createOrderedNode(this.entities.createArtifact(data));
    }

    private Node.Op child(Node.Op parent, ArtifactData data) {
        Node.Op child = this.node(data);
        parent.addChild(child);
        return child;
    }

    private void lines(Node.Op parent, String... lines) {
        for (String line : lines)
            this.child(parent, new LineArtifactData(line));
    }
}
