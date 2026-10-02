package at.jku.isse.ecco.adapter.challenge;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JavaWriter is the writer bound for the challenge adapter (SPLC feature location challenge), but it cannot rebuild Java source
 * from the tree JavaBlockReader builds. It used to write nothing and return no files, so a checkout
 * looked successful while every .java file was missing. It now refuses, naming the adapter to use.
 */
public class JavaWriterTest {

    private final JavaWriter writer = new JavaWriter();

    @Test
    public void writingFilesFailsAndNamesTheJavaAstAdapter() {
        SerEntityFactory ef = new SerEntityFactory();
        Node.Op pluginNode = ef.createNode(ef.createArtifact(new PluginArtifactData(JavaPlugin.class.getName(), Path.of("Foo.java"))));

        EccoException e = assertThrows(EccoException.class, () -> writer.write(Path.of("."), Set.<Node>of(pluginNode)));
        assertTrue(e.getMessage().contains("JavaASTPlugin"), e.getMessage());
    }

    @Test
    public void writeOfAnEmptySetProducesNoOutput() {
        Path[] written = writer.write(Path.of("."), Set.of());

        assertEquals(0, written.length);
    }

    @Test
    public void getPluginIdIsTheJavaPluginClassName() {
        assertEquals(JavaPlugin.class.getName(), writer.getPluginId());
    }

    @Test
    public void addAndRemoveListenerDoNotThrow() {
        // WriteListener's sole method is a no-op default, so it has zero abstract methods and isn't
        // lambda-expressible - an anonymous instance using the default is the only option here.
        WriteListener listener = new WriteListener() {};

        assertDoesNotThrow(() -> {
            writer.addListener(listener);
            writer.removeListener(listener);
        });
    }
}
