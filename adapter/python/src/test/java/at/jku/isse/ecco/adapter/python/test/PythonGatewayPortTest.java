package at.jku.isse.ecco.adapter.python.test;

import at.jku.isse.ecco.adapter.python.PythonReader;
import at.jku.isse.ecco.adapter.python.PythonRenderer;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The adapter talks to its python script through a py4j gateway, which listened on py4j's fixed
 * default port 25333: a second ECCO process reading or writing Python files at the same time (the
 * GUI and the command line, two REST requests) failed with "Address already in use". The gateway
 * now listens on a free port, passed to the script.
 */
public class PythonGatewayPortTest {

    private static boolean pythonAvailable() {
        try {
            Process process = new ProcessBuilder("python", "-c", "import libcst, py4j").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Test
    @Timeout(120)
    public void readsAndRendersWhileTheDefaultPortIsTaken() throws Exception {
        assumeTrue(pythonAvailable(), "needs `python` with the libcst and py4j modules on the PATH");
        Path base = Files.createTempDirectory("python-gateway-port");
        Files.writeString(base.resolve("m.py"), "x = 1\n\ndef f(a):\n    return a\n");
        // what another ECCO process's gateway looked like
        try (ServerSocket taken = new ServerSocket()) {
            // bound like py4j binds it: the loopback address
            taken.bind(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 25333));
            Set<Node.Op> nodes = new PythonReader(new SerEntityFactory()).read(base, new Path[]{base.resolve("m.py")});
            Node fileNode = nodes.iterator().next();
            assertFalse(fileNode.getChildren().isEmpty(), "the file must have been parsed");
            assertTrue(PythonRenderer.render(fileNode).getText().contains("return a"));
        }
    }
}
