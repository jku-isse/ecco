package at.jku.isse.ecco.adapter.cpp;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.cpp.data.SourceLineArtifactData;
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

/** The C++ reader and writer apart from parsing: failures, the variant's .config, listeners. */
public class CppReaderWriterTest {

    private final EntityFactory entities = new SerEntityFactory();

    /** an unreadable file fails the commit rather than being committed empty */
    @Test
    public void aFileThatCannotBeReadFailsTheRead(@TempDir Path base) throws IOException {
        Files.createDirectories(base.resolve("dir.cpp"));
        EccoException e = assertThrows(EccoException.class, () -> new CppReader(this.entities).read(base, new Path[]{Path.of("dir.cpp")}));
        assertTrue(e.getMessage().contains("Could not read file"), e.getMessage());
    }

    @Test
    public void theConfigurationIsTheFirstLineOfConfig(@TempDir Path base) throws IOException {
        Files.writeString(base.resolve(".config"), "A, B\nignored\n");
        assertEquals("A, B", this.configurationOf(base));
        assertTrue(new CppReader(this.entities).readContext(base).startsWith("A, B\n"));
    }

    /** an empty .config is an empty configuration (the C reader fails on it) */
    @Test
    public void anEmptyConfigIsAnEmptyConfiguration(@TempDir Path base) throws IOException {
        Files.writeString(base.resolve(".config"), "");
        assertEquals("", this.configurationOf(base));
    }

    @Test
    public void aConfigThatCannotBeOpenedFailsTheRead(@TempDir Path base) throws IOException {
        Path config = Files.writeString(base.resolve(".config"), "A\n");
        Files.writeString(base.resolve("main.cpp"), "int x;\n");
        assumeTrue(config.toFile().setReadable(false) && !Files.isReadable(config), "file permissions not enforced (root?)");
        try {
            EccoException e = assertThrows(EccoException.class, () -> new CppReader(this.entities).read(base, new Path[]{Path.of("main.cpp")}));
            assertTrue(e.getMessage().contains(".config"), e.getMessage());
        } finally {
            config.toFile().setReadable(true);
        }
    }

    /**
     * A .config that opens but cannot be read (a directory) fails while the stream is read: an
     * UncheckedIOException, which getConfigurationString's catch (IOException) does not wrap.
     */
    @Test
    public void aConfigDirectoryFailsTheReadUnwrapped(@TempDir Path base) throws IOException {
        Files.createDirectories(base.resolve(".config"));
        Files.writeString(base.resolve("main.cpp"), "int x;\n");
        assertThrows(UncheckedIOException.class, () -> new CppReader(this.entities).read(base, new Path[]{Path.of("main.cpp")}));
    }

    /** without a base the input is resolved against the working directory: an absolute path as is */
    @Test
    public void readAndWriteWithoutABase(@TempDir Path base) throws IOException {
        Path file = Files.writeString(base.resolve("main.cpp"), "int x;\n");
        Set<Node.Op> read = new CppReader(this.entities).read(new Path[]{file});
        assertEquals(1, read.size());
        Files.delete(file);

        Path[] written = new CppWriter().write(Set.copyOf(read));
        assertArrayEquals(new Path[]{file}, written);
        assertEquals("int x;\n", Files.readString(file));
    }

    @Test
    public void onlyFileNodesCanBeWritten(@TempDir Path base) {
        Node.Op line = this.entities.createNode(this.entities.createArtifact(new SourceLineArtifactData("int x;")));
        EccoException e = assertThrows(EccoException.class, () -> new CppWriter().write(base, Set.of(line)));
        assertEquals("Expected plugin artifact data.", e.getMessage());
    }

    @Test
    public void anArtifactOfAnotherAdapterFailsTheWrite(@TempDir Path base) {
        Node.Op file = this.file(Path.of("main.cpp"));
        file.addChild(this.entities.createNode(this.entities.createArtifact(new PluginArtifactData("other", Path.of("x")))));
        EccoException e = assertThrows(EccoException.class, () -> new CppWriter().write(base, Set.of(file)));
        assertTrue(e.getMessage().startsWith("Unexpected artifact in a C++ file"), e.getMessage());
    }

    @Test
    public void aFileThatCannotBeWrittenFailsTheWrite(@TempDir Path base) throws IOException {
        Files.writeString(base.resolve("not-a-dir"), "");
        Node.Op file = this.file(Path.of("not-a-dir", "main.cpp"));
        file.addChild(this.entities.createNode(this.entities.createArtifact(new SourceLineArtifactData("int x;"))));
        EccoException e = assertThrows(EccoException.class, () -> new CppWriter().write(base, Set.of(file)));
        assertTrue(e.getMessage().contains("Could not write file"), e.getMessage());
    }

    /** the listeners are kept, but neither reader nor writer notifies them (DispatchReader/DispatchWriter do) */
    @Test
    public void listenersCanBeAddedAndRemoved() {
        CppReader reader = new CppReader(this.entities);
        ReadListener readListener = new ReadListener() {};
        reader.addListener(readListener);
        reader.removeListener(readListener);
        CppWriter writer = new CppWriter();
        WriteListener writeListener = new WriteListener() {};
        writer.addListener(writeListener);
        writer.removeListener(writeListener);
        assertEquals(CppPlugin.class.getName(), reader.getPluginId());
        assertEquals(CppPlugin.class.getName(), writer.getPluginId());
    }

    private String configurationOf(Path base) throws IOException {
        Files.writeString(base.resolve("main.cpp"), "int x;\n");
        Node file = new CppReader(this.entities).read(base, new Path[]{Path.of("main.cpp")}).iterator().next();
        return file.getChildren().get(0).<Location>getProperty("Location").orElseThrow().getConfigurationString();
    }

    private Node.Op file(Path path) {
        return this.entities.createOrderedNode(this.entities.createArtifact(new PluginArtifactData(CppPlugin.class.getName(), path)));
    }
}
