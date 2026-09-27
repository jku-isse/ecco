package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reading a TypeScript file and writing it back must give the same text. Variable statements lost
 * everything after their last declaration - `let x = 1;` came back as `let x = 1` - and a file with
 * a JSDoc comment could not be read at all.
 */
public class TypeScriptRoundTripTest {

    @ParameterizedTest
    @Timeout(120)
    @ValueSource(strings = {
            "let x: number = 1;\n",
            "const s = \"a\";\nlet y = 2\n",
            "let a = 1, b = 2;\n",
            "export const c = 3; // the answer\nvar d = 4;\n",
            "const f = (s: string) => s.length;\n",
            "let x = 1;\nfunction g(a: string) {\n  const z = a;\n  return z;\n}\n",
            // JSDoc comments made every commit fail (a circular structure in the parser's output)
            "/** A function. */\nfunction f(a: number) {\n    return a;\n}\n",
            "class K {\n    /** A method. */\n    m() {\n        return 1;\n    }\n}\n",
            "interface D {\n    /** The description. */\n    readonly d: string;\n}\n",
    })
    public void aFileIsWrittenBackAsItWasRead(String source) throws Exception {
        Path base = Files.createTempDirectory("typescript-round-trip");
        Files.writeString(base.resolve("main.ts"), source);
        Node fileNode = new TypeScriptReader(new SerEntityFactory()).read(base, new Path[]{Paths.get("main.ts")}).iterator().next();

        Path out = Files.createTempDirectory("typescript-round-trip-out");
        new TypeScriptWriter().write(out, Set.of(fileNode));

        assertEquals(source, Files.readString(out.resolve("main.ts")));
    }
}
