package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JavaWriter is the writer actually bound for the Java adapter (JavaModule.configure() binds it
 * alongside JavaBlockReader), but its write() method is an unimplemented stub - literally
 * "// TODO: implement!" followed by returning an empty array regardless of input. Checking out a
 * Java-based variant currently writes nothing back to disk for any Java file. Characterizing that
 * real, surprising limitation as-is rather than silently padding a coverage number - not fixed here,
 * since implementing a real writer (reconstructing Java source from the class/method/field/block
 * tree JavaBlockReader builds) is substantial new functionality, not a test-only change.
 */
public class JavaWriterTest {

    private final JavaWriter writer = new JavaWriter();

    @Test
    public void writeProducesNoOutputRegardlessOfInput() {
        SerEntityFactory ef = new SerEntityFactory();
        Node.Op pluginNode = ef.createNode(ef.createArtifact(new PluginArtifactData(JavaPlugin.class.getName(), Path.of("Foo.java"))));

        Path[] written = writer.write(Path.of("."), Set.<Node>of(pluginNode));

        assertEquals(0, written.length, "write() is an unimplemented stub - it never produces any output file, even for real input");
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
