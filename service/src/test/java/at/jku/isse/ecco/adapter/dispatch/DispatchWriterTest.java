package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import com.sun.management.UnixOperatingSystemMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DispatchWriter.writeRec() used to catch IOException from Files.createDirectory() with only
 * e.printStackTrace(), then keep going as if nothing happened -- write() would return "successfully"
 * (and write the .hashes file) even though the failed subtree's content was never written to disk.
 * Also, writeRec() called writer.write(...) on whatever getWriterForArtifact() returned without a
 * null check, throwing a raw NullPointerException instead of a clear error when no writer was
 * registered for a plugin id. And write()'s top-level emptiness check consumed the Stream returned
 * by Files.list(base) without closing it, leaking the underlying directory handle on every call.
 */
public class DispatchWriterTest {

	@Test
	@Timeout(30)
	public void writeThrowsInsteadOfSilentlySwallowingADirectoryCreateFailure() throws IOException {
		Path base = Files.createTempDirectory("dispatch-writer-dircreate");
		// "preexisting" doubles as the repositoryDir so write()'s "base must be empty" pre-check
		// (which excludes only repositoryDir) still passes even though it already exists on disk --
		// the only way a top-level target path can legitimately collide with something already there.
		Path repoDir = base.resolve("preexisting");
		Files.createDirectories(repoDir);

		DispatchWriter writer = new DispatchWriter(Set.of(), repoDir);

		SerEntityFactory ef = new SerEntityFactory();
		Node.Op dirNode = ef.createNode(new DirectoryArtifactData(Path.of("preexisting")));

		assertThrows(EccoException.class, () -> writer.write(base, Set.of(dirNode)),
				"a directory that fails to be created must abort the checkout, not be silently skipped");
		assertFalse(Files.exists(base.resolve(EccoService.HASHES_FILE_NAME)),
				"write() must not finish 'successfully' (and write the hashes file) after silently losing a subtree");
	}

	@Test
	@Timeout(30)
	public void writeThrowsClearErrorForUnregisteredWriterInsteadOfNpe() throws IOException {
		Path base = Files.createTempDirectory("dispatch-writer-nowriter");
		Path repoDir = base.resolve(".ecco");

		DispatchWriter writer = new DispatchWriter(Set.of(), repoDir);

		SerEntityFactory ef = new SerEntityFactory();
		Node.Op fileNode = ef.createNode(new PluginArtifactData("nonexistent-plugin", Path.of("file.txt")));

		EccoException exception = assertThrows(EccoException.class, () -> writer.write(base, Set.of(fileNode)),
				"a missing writer registration must surface as a clear error, not a raw NullPointerException");
		assertTrue(exception.getMessage() != null && exception.getMessage().contains("nonexistent-plugin"));
	}

	@Test
	@Timeout(60)
	public void writeDoesNotLeakDirectoryStreamHandles() throws IOException {
		Path base = Files.createTempDirectory("dispatch-writer-leak");
		Path repoDir = base.resolve(".ecco");
		DispatchWriter writer = new DispatchWriter(Set.of(), repoDir);
		Path hashesFile = base.resolve(EccoService.HASHES_FILE_NAME);

		UnixOperatingSystemMXBean osBean =
				(UnixOperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

		long before = osBean.getOpenFileDescriptorCount();
		for (int i = 0; i < 3000; i++) {
			writer.write(base, Set.of());
			Files.delete(hashesFile);
		}
		long after = osBean.getOpenFileDescriptorCount();

		assertTrue(after - before < 200,
				"open file descriptor count grew by " + (after - before) + " after 3000 write() calls " +
						"-- Files.list(base) in write()'s emptiness check is not being closed");
	}
}
