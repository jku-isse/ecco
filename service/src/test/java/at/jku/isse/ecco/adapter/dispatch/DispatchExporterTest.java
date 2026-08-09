package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.EccoException;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DispatchExporter is a near-duplicate of DispatchWriter and had the exact same three bugs fixed
 * there this session: exportRec() swallowed IOException from Files.createDirectory() with only
 * e.printStackTrace() (silently truncating the export instead of failing it), export()'s top-level
 * emptiness check leaked the Stream from Files.list(base), and exportRec()'s PluginArtifactData
 * branch called exporter.export(...) without a null check, throwing a raw NullPointerException
 * instead of a clear error when no exporter was registered for a plugin id.
 */
public class DispatchExporterTest {

	@Test
	@Timeout(30)
	public void exportThrowsInsteadOfSilentlySwallowingADirectoryCreateFailure() throws IOException {
		Path base = Files.createTempDirectory("dispatch-exporter-dircreate");
		// "preexisting" doubles as the repositoryDir so export()'s "base must be empty" pre-check
		// (which excludes only repositoryDir) still passes even though it already exists on disk --
		// the only way a top-level target path can legitimately collide with something already there.
		Path repoDir = base.resolve("preexisting");
		Files.createDirectories(repoDir);

		DispatchExporter exporter = new DispatchExporter(Set.of(), repoDir);

		SerEntityFactory ef = new SerEntityFactory();
		Node.Op dirNode = ef.createNode(new DirectoryArtifactData(Path.of("preexisting")));

		assertThrows(EccoException.class, () -> exporter.export(base, Set.of(dirNode)),
				"a directory that fails to be created must abort the export, not be silently skipped");
	}

	@Test
	@Timeout(30)
	public void exportThrowsClearErrorForUnregisteredExporterInsteadOfNpe() throws IOException {
		Path base = Files.createTempDirectory("dispatch-exporter-noexporter");
		Path repoDir = base.resolve(".ecco");

		DispatchExporter exporter = new DispatchExporter(Set.of(), repoDir);

		SerEntityFactory ef = new SerEntityFactory();
		Node.Op fileNode = ef.createNode(new PluginArtifactData("nonexistent-plugin", Path.of("file.txt")));

		EccoException exception = assertThrows(EccoException.class, () -> exporter.export(base, Set.of(fileNode)),
				"a missing exporter registration must surface as a clear error, not a raw NullPointerException");
		assertTrue(exception.getMessage() != null && exception.getMessage().contains("nonexistent-plugin"));
	}

	@Test
	@Timeout(60)
	public void exportDoesNotLeakDirectoryStreamHandles() throws IOException {
		Path base = Files.createTempDirectory("dispatch-exporter-leak");
		Path repoDir = base.resolve(".ecco");
		DispatchExporter exporter = new DispatchExporter(Set.of(), repoDir);

		UnixOperatingSystemMXBean osBean =
				(UnixOperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

		long before = osBean.getOpenFileDescriptorCount();
		for (int i = 0; i < 3000; i++) {
			exporter.export(base, Set.of());
		}
		long after = osBean.getOpenFileDescriptorCount();

		assertTrue(after - before < 200,
				"open file descriptor count grew by " + (after - before) + " after 3000 export() calls " +
						"-- Files.list(base) in export()'s emptiness check is not being closed");
	}
}
