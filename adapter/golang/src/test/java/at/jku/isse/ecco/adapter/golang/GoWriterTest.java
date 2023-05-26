package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.adapter.golang.io.MemorySourceWriter;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class GoWriterTest {
    @Test
    public void pluginIdIsEqualToGoPluginId() {
        assertEquals(new GoPlugin().getPluginId(), new GoWriter(new MemorySourceWriter()).getPluginId());
    }

    @Test
    public void reconstructSimpleGolangExample() throws IOException {
        URL simpleGoResource = getClass().getClassLoader().getResource("simple.go");
        Path resourcePath = null;
        try {
            assertNotNull(simpleGoResource);
            resourcePath = Path.of(simpleGoResource.toURI());
        } catch (URISyntaxException e) {
            fail(e);
        }
        Set<Node.Op> resultSet = new GoReader(new MemEntityFactory()).read(Path.of("."), new Path[]{resourcePath});
        MemorySourceWriter sourceWriter = new MemorySourceWriter();
        GoWriter goWriter = new GoWriter(sourceWriter);
        Path[] returnedFiles = goWriter.write(resultSet.stream().map(nodeOp -> (Node)nodeOp).collect(Collectors.toSet()));
        Map<Path, String> writtenFiles = sourceWriter.getWrittenFiles();

        assertTrue(writtenFiles.containsKey(resourcePath));

        for (Path path: returnedFiles) {
            assertTrue(writtenFiles.containsKey(path));
        }

        assertEquals(Files.readString(resourcePath), sourceWriter.getWrittenFiles().get(resourcePath));
    }

    @Test
    public void listenerIsCalled() {
        URL simpleGoResource = getClass().getClassLoader().getResource("simple.go");
        Path resourcePath = null;
        try {
            assertNotNull(simpleGoResource);
            resourcePath = Path.of(simpleGoResource.toURI());
        } catch (URISyntaxException e) {
            fail(e);
        }
        Set<Node.Op> resultSet = new GoReader(new MemEntityFactory()).read(Path.of("."), new Path[]{resourcePath});
        MemorySourceWriter sourceWriter = new MemorySourceWriter();
        GoWriter goWriter = new GoWriter(sourceWriter);
        WriteListener writeListener = mock(WriteListener.class);

        goWriter.addListener(writeListener);
        goWriter.write(resultSet.stream().map(nodeOp -> (Node)nodeOp).collect(Collectors.toSet()));

        verify(writeListener).fileWriteEvent(eq(resourcePath), any());
    }

    @Test
    public void listenerIsRemoved() {
        URL simpleGoResource = getClass().getClassLoader().getResource("simple.go");
        Path resourcePath = null;
        try {
            assertNotNull(simpleGoResource);
            resourcePath = Path.of(simpleGoResource.toURI());
        } catch (URISyntaxException e) {
            fail(e);
        }
        Set<Node.Op> resultSet = new GoReader(new MemEntityFactory()).read(Path.of("."), new Path[]{resourcePath});
        MemorySourceWriter sourceWriter = new MemorySourceWriter();
        GoWriter goWriter = new GoWriter(sourceWriter);
        WriteListener writeListener = mock(WriteListener.class);

        goWriter.addListener(writeListener);
        goWriter.removeListener(writeListener);
        goWriter.write(resultSet.stream().map(nodeOp -> (Node)nodeOp).collect(Collectors.toSet()));

        verify(writeListener, never()).fileWriteEvent(eq(resourcePath), any());
    }
}
