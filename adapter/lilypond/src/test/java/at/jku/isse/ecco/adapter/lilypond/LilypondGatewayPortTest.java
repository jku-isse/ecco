package at.jku.isse.ecco.adapter.lilypond;

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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The parser script talks to the adapter through a py4j gateway, which listened on py4j's fixed
 * default port 25333: a second ECCO process reading LilyPond files at the same time failed with
 * "Address already in use". The gateway now listens on a free port, passed to the script.
 */
public class LilypondGatewayPortTest {

	private static boolean parceAvailable() {
		try {
			Process process = new ProcessBuilder("python", "-c", "import parce, py4j").redirectErrorStream(true).start();
			process.getInputStream().readAllBytes();
			return process.waitFor() == 0;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	@Test
	@Timeout(120)
	void readsWhileTheDefaultPortIsTaken() throws Exception {
		assumeTrue(parceAvailable(), "needs `python` with the parce and py4j modules on the PATH");
		Path base = Files.createTempDirectory("lilypond-gateway-port");
		Files.writeString(base.resolve("a.ly"), "\\\\version \"2.24.0\"\n{ c'4 d'4 e'4 }\n");
		// what another ECCO process's gateway looked like
		try (ServerSocket taken = new ServerSocket()) {
            // bound like py4j binds it: the loopback address
            taken.bind(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 25333));
			Set<Node.Op> nodes = new LilypondReader(new SerEntityFactory()).read(base, new Path[]{Path.of("a.ly")});
			assertFalse(nodes.iterator().next().getChildren().isEmpty(), "the file must have been parsed");
		}
	}
}
