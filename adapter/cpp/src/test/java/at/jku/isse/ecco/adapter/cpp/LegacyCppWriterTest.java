package at.jku.isse.ecco.adapter.cpp;

import at.jku.isse.ecco.adapter.cpp.data.*;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repositories committed with the first C++ adapter can still be checked out (NFR-V2): CppWriter hands
 * their trees to LegacyCppWriter. Nothing else exercised it. This pins down what it writes for a
 * tree shaped like the ones the first reader built (see CppReader before 9639e362): the INCLUDES,
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
