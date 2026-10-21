package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.adapter.c.CReader;
import at.jku.isse.ecco.adapter.file.FileReader;
import at.jku.isse.ecco.adapter.text.TextReader;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A file read with the same content and context as in the last read is not read again: its tree is
 * copied (Git import reads every file of every commit, most of them unchanged). What a C file's tree
 * depends on besides the file - the configuration in .config, VEVOS conditions in pcs.variant.csv -
 * is part of what must be the same.
 */
@Timeout(120)
public class DispatchReaderReuseTest {

    @Test
    public void unchangedFilesAreNotReadAgain_changedOnesAndAChangedContextAre(@TempDir Path tmp) throws Exception {
        Path content = Files.createDirectories(tmp.resolve("content"));
        Files.writeString(content.resolve("a.c"), "int a(void) {\n    return 1;\n}\n");
        Files.writeString(content.resolve("b.c"), "int b(void) {\n    return 2;\n}\n");
        Files.writeString(content.resolve("notes.txt"), "text is read again: as cheap as a copy\n");
        SerEntityFactory entityFactory = new SerEntityFactory();
        DispatchReader reader = new DispatchReader(entityFactory,
                Set.of(new CReader(entityFactory), new TextReader(entityFactory), new FileReader(entityFactory)),
                Files.createDirectories(tmp.resolve(".ecco")));
        reader.init();
        Path[] all = {Path.of(".")};

        reader.read(content, all);
        assertEquals(0, reader.lastReused());
        reader.read(content, all);
        assertEquals(2, reader.lastReused());

        Files.writeString(content.resolve("a.c"), "int a(void) {\n    return 3;\n}\n");
        reader.read(content, all);
        assertEquals(1, reader.lastReused(), "a.c changed");

        Files.writeString(content.resolve(".config"), "A.1\n");
        reader.read(content, all);
        assertEquals(0, reader.lastReused(), "lines carry the configuration");
        reader.read(content, all);
        assertEquals(2, reader.lastReused());

        Files.writeString(content.resolve("pcs.variant.csv"), "Path;File Condition;Block Condition;Presence Condition;start;end\n");
        reader.read(content, all);
        assertEquals(0, reader.lastReused(), "lines carry VEVOS conditions");
    }

    /** FR-M11c: with -Decco.reuseReadTrees=false every file is read every time. */
    @Test
    public void withReuseOffEveryFileIsReadAgain(@TempDir Path tmp) throws Exception {
        Path content = Files.createDirectories(tmp.resolve("content"));
        Files.writeString(content.resolve("a.c"), "int a(void) {\n    return 1;\n}\n");
        SerEntityFactory entityFactory = new SerEntityFactory();
        DispatchReader reader = new DispatchReader(entityFactory,
                Set.of(new CReader(entityFactory), new TextReader(entityFactory), new FileReader(entityFactory)),
                Files.createDirectories(tmp.resolve(".ecco")));
        reader.init();
        Path[] all = {Path.of(".")};
        String previous = System.getProperty("ecco.reuseReadTrees");
        System.setProperty("ecco.reuseReadTrees", "false");
        try {
            reader.read(content, all);
            reader.read(content, all);
            assertEquals(0, reader.lastReused());
        } finally {
            if (previous == null) System.clearProperty("ecco.reuseReadTrees");
            else System.setProperty("ecco.reuseReadTrees", previous);
        }
    }

    @Test
    public void commitsWithAndWithoutReuseCheckOutTheSame(@TempDir Path tmp) throws Exception {
        List<List<String>> versions = List.of(
                List.of("int a(void) {\n    return 1;\n}\n", "int b(void) {\n    return 2;\n}\n"),
                List.of("int a(void) {\n    return 1;\n}\n", "int b(void) {\n    return 2;\n}\nint c(void) {\n    return 3;\n}\n"),
                List.of("int a(void) {\n    return 4;\n}\n", "int b(void) {\n    return 2;\n}\nint c(void) {\n    return 3;\n}\n"));
        String previous = System.getProperty("ecco.reuseReadTrees");
        try {
            for (String reuse : List.of("true", "false")) {
                System.setProperty("ecco.reuseReadTrees", reuse);
                Path root = Files.createDirectories(tmp.resolve(reuse));
                try (EccoService service = new EccoService()) {
                    service.setRepositoryDir(root.resolve(".ecco"));
                    service.init();
                    for (int i = 0; i < versions.size(); i++) {
                        Path dir = Files.createDirectories(root.resolve("v" + i));
                        Files.writeString(dir.resolve("a.c"), versions.get(i).get(0));
                        Files.writeString(dir.resolve("b.c"), versions.get(i).get(1));
                        service.setBaseDir(dir);
                        service.commit("v" + i, "BASE, V" + i);
                    }
                    for (int i = 0; i < versions.size(); i++) {
                        Path out = Files.createDirectories(root.resolve("out" + i));
                        service.setBaseDir(out);
                        service.checkout("BASE, V" + i);
                    }
                }
            }
        } finally {
            if (previous == null) System.clearProperty("ecco.reuseReadTrees");
            else System.setProperty("ecco.reuseReadTrees", previous);
        }
        for (int i = 0; i < versions.size(); i++)
            for (String file : List.of("a.c", "b.c")) {
                byte[] reused = Files.readAllBytes(tmp.resolve("true/out" + i).resolve(file));
                assertArrayEquals(Files.readAllBytes(tmp.resolve("false/out" + i).resolve(file)), reused, "v" + i + " " + file);
                assertEquals(versions.get(i).get(file.equals("a.c") ? 0 : 1), new String(reused), "v" + i + " " + file);
            }
    }
}
