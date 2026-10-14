package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.c.data.LineArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The C reader and writer apart from parsing: failures, the variant's .config, listeners. */
public class CReaderWriterTest {

    private final EntityFactory entities = new SerEntityFactory();

    /** an unreadable file fails the commit rather than being committed empty */
    @Test
    public void aFileThatCannotBeReadFailsTheRead(@TempDir Path base) throws IOException {
        Files.createDirectories(base.resolve("dir.c"));
        EccoException e = assertThrows(EccoException.class, () -> new CReader(this.entities).read(base, new Path[]{Path.of("dir.c")}));
        assertTrue(e.getMessage().contains("Could not read file"), e.getMessage());
    }

    /** the first line of the variant's .config goes into every line's Location */
    @Test
    public void theConfigurationIsTheFirstLineOfConfig(@TempDir Path base) throws IOException {
        Files.writeString(base.resolve(".config"), "A, B\nignored\n");
        Files.writeString(base.resolve("main.c"), "int x;\n");
        Node file = new CReader(this.entities).read(base, new Path[]{Path.of("main.c")}).iterator().next();
        Location location = file.getChildren().get(0).<Location>getProperty("Location").orElseThrow();
        assertEquals("A, B", location.getConfigurationString());
        assertTrue(new CReader(this.entities).readContext(base).startsWith("A, B\n"));
    }

    /**
     * An empty .config has no first line: CReader#getConfigurationString takes it with get(0) and
     * fails the read (CppReader takes "" instead). Current behaviour.
     */
    @Test
    public void anEmptyConfigFailsTheRead(@TempDir Path base) throws IOException {
        Files.writeString(base.resolve(".config"), "");
        Files.writeString(base.resolve("main.c"), "int x;\n");
        assertThrows(IndexOutOfBoundsException.class, () -> new CReader(this.entities).read(base, new Path[]{Path.of("main.c")}));
    }

    /** a plain RuntimeException wrapping the IOException, not an EccoException */
    @Test
    public void aConfigThatCannotBeOpenedFailsTheRead(@TempDir Path base) throws IOException {
        Path config = Files.writeString(base.resolve(".config"), "A\n");
        Files.writeString(base.resolve("main.c"), "int x;\n");
        assumeTrue(config.toFile().setReadable(false) && !Files.isReadable(config), "file permissions not enforced (root?)");
        try {
            RuntimeException e = assertThrows(RuntimeException.class, () -> new CReader(this.entities).read(base, new Path[]{Path.of("main.c")}));
            assertInstanceOf(IOException.class, e.getCause());
        } finally {
            config.toFile().setReadable(true);
        }
    }

    /** a .config that opens but cannot be read (a directory) fails while the stream is read, unwrapped */
    @Test
    public void aConfigDirectoryFailsTheReadUnwrapped(@TempDir Path base) throws IOException {
        Files.createDirectories(base.resolve(".config"));
        Files.writeString(base.resolve("main.c"), "int x;\n");
        assertThrows(UncheckedIOException.class, () -> new CReader(this.entities).read(base, new Path[]{Path.of("main.c")}));
    }

    /** without a base the input is resolved against the working directory: an absolute path as is */
    @Test
    public void readWithoutABase(@TempDir Path base) throws IOException {
        Path file = Files.writeString(base.resolve("main.c"), "int x;\n");
        Set<Node.Op> read = new CReader(this.entities).read(new Path[]{file});
        assertEquals(1, read.size());
        assertEquals(file, ((PluginArtifactData) read.iterator().next().getArtifact().getData()).getPath());
    }

    /** write without a base writes nothing at all (CppWriter writes to the working directory) */
    @Test
    public void writeWithoutABaseWritesNothing(@TempDir Path base) {
        Node.Op file = this.file(base.resolve("main.c"));
        file.addChild(this.entities.createNode(this.entities.createArtifact(new LineArtifactData("int x;"))));
        assertEquals(0, new CWriter().write(Set.of(file)).length);
        assertFalse(Files.exists(base.resolve("main.c")));
    }

    @Test
    public void onlyFileNodesCanBeWritten(@TempDir Path base) {
        Node.Op line = this.entities.createNode(this.entities.createArtifact(new LineArtifactData("int x;")));
        EccoException e = assertThrows(EccoException.class, () -> new CWriter().write(base, Set.of(line)));
        assertEquals("Expected plugin artifact data.", e.getMessage());
    }

    @Test
    public void anArtifactOfAnotherAdapterFailsTheWrite(@TempDir Path base) {
        Node.Op file = this.file(Path.of("main.c"));
        file.addChild(this.entities.createNode(this.entities.createArtifact(new PluginArtifactData("other", Path.of("x")))));
        assertThrows(EccoException.class, () -> new CWriter().write(base, Set.of(file)));
    }

    @Test
    public void aFileThatCannotBeWrittenFailsTheWrite(@TempDir Path base) throws IOException {
        Files.writeString(base.resolve("not-a-dir"), "");
        Node.Op file = this.file(Path.of("not-a-dir", "main.c"));
        EccoException e = assertThrows(EccoException.class, () -> new CWriter().write(base, Set.of(file)));
        assertTrue(e.getMessage().contains("Could not write file"), e.getMessage());
    }

    /** the listeners are kept, but neither reader nor writer notifies them (DispatchReader/DispatchWriter do) */
    @Test
    public void listenersCanBeAddedAndRemoved(@TempDir Path base) throws IOException {
        CReader reader = new CReader(this.entities);
        ReadListener readListener = new ReadListener() {};
        reader.addListener(readListener);
        reader.removeListener(readListener);
        CWriter writer = new CWriter();
        WriteListener writeListener = new WriteListener() {};
        writer.addListener(writeListener);
        writer.removeListener(writeListener);
        assertEquals(CPlugin.class.getName(), reader.getPluginId());
        assertEquals(CPlugin.class.getName(), writer.getPluginId());
    }

    private Node.Op file(Path path) {
        return this.entities.createOrderedNode(this.entities.createArtifact(new PluginArtifactData(CPlugin.class.getName(), path)));
    }
}
