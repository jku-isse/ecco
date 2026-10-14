package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.core.Remote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pull and push merge only files both repositories read the same way (.ecco/.settings): LilyPond read
 * with musical tokens and without match nothing of each other. A receiving repository with no LilyPond
 * settings and no LilyPond files takes the sender's over, as a fork does; one with other settings, or
 * with LilyPond files but no settings, refuses - before anything is merged. Files of plugins without
 * settings are merged as before.
 */
public class LilypondSettingsSyncTest {

	private static final Path V2 = LilypondVariantsCommitCheckoutTest.findRepoRoot()
			.resolve("examples").resolve("lilypond_variants").resolve("v2_setup_notes");

	private static Properties settings(Path repositoryDir) throws IOException {
		Properties properties = new Properties();
		try (Reader in = Files.newBufferedReader(repositoryDir.resolve(EccoService.SETTINGS_FILE_NAME))) {
			properties.load(in);
		}
		return properties;
	}

	/** Sets [key] to [value] in the repository's settings, or removes it when [value] is null. */
	private static void setting(Path repositoryDir, String key, String value) throws IOException {
		Properties settings = settings(repositoryDir);
		if (value == null)
			settings.remove(key);
		else
			settings.setProperty(key, value);
		try (Writer out = Files.newBufferedWriter(repositoryDir.resolve(EccoService.SETTINGS_FILE_NAME))) {
			settings.store(out, null);
		}
	}

	private static void withoutLilypondSettings(Path repositoryDir) throws IOException {
		for (String key : new LilypondPlugin().newRepositorySettings().keySet())
			setting(repositoryDir, key, null);
	}

	private static EccoService newRepository(String name) throws IOException {
		EccoService service = new EccoService();
		service.setRepositoryDir(Files.createTempDirectory(name).resolve(".ecco"));
		service.init();
		return service;
	}

	private static EccoService open(Path repositoryDir) {
		EccoService service = new EccoService();
		service.setRepositoryDir(repositoryDir);
		service.open();
		return service;
	}

	private static void commitLilypond(EccoService service) {
		service.setBaseDir(V2);
		service.commit("v2", "setup.1, notes.1");
	}

	private static void commitText(EccoService service) throws IOException {
		Path dir = Files.createTempDirectory("settings-sync-text");
		Files.writeString(dir.resolve("a.txt"), "a\n");
		service.setBaseDir(dir);
		service.commit("text", "text.1");
	}

	private static void assertRefusedFor(String setting, Throwable thrown) {
		for (Throwable t = thrown; t != null; t = t.getCause())
			if (String.valueOf(t.getMessage()).contains("read differently") && t.getMessage().contains(setting)) {
				assertFalse(t.getMessage().contains(LilypondPlugin.PARSER_SETTING), "names only the settings that differ: " + t.getMessage());
				return;
			}
		throw new AssertionError("expected a refusal naming " + setting, thrown);
	}

	@Test
	@Timeout(120)
	public void aPullOfFilesReadDifferentlyIsRefused() throws Exception {
		assumeTrue(LilypondTraceMeasure.readable(), LilypondTraceMeasure.UNREADABLE);
		try (EccoService origin = newRepository("settings-sync-origin"); EccoService target = newRepository("settings-sync-target")) {
			commitLilypond(origin);
			setting(target.getRepositoryDir(), LilypondPlugin.MUSICAL_TOKENS_SETTING, "false");
			target.addRemote("origin", origin.getRepositoryDir().getParent().toString(), Remote.Type.LOCAL);

			assertRefusedFor(LilypondPlugin.MUSICAL_TOKENS_SETTING, assertThrows(EccoException.class, () -> target.pull("origin")));
			assertTrue(target.getRepository().getFeatures().isEmpty(), "nothing merged");
			assertEquals("false", settings(target.getRepositoryDir()).getProperty(LilypondPlugin.MUSICAL_TOKENS_SETTING));
		}
	}

	@Test
	@Timeout(120)
	public void aRepositoryWithoutLilypondSettingsOrFilesTakesTheOnesItPulls() throws Exception {
		assumeTrue(LilypondTraceMeasure.readable(), LilypondTraceMeasure.UNREADABLE);
		try (EccoService origin = newRepository("settings-sync-origin"); EccoService target = newRepository("settings-sync-target")) {
			setting(origin.getRepositoryDir(), LilypondPlugin.MUSICAL_TOKENS_SETTING, "false");
			commitLilypond(origin);
			withoutLilypondSettings(target.getRepositoryDir());
			target.addRemote("origin", origin.getRepositoryDir().getParent().toString(), Remote.Type.LOCAL);

			target.pull("origin");

			assertFalse(target.getRepository().getFeatures().isEmpty());
			assertEquals("false", settings(target.getRepositoryDir()).getProperty(LilypondPlugin.MUSICAL_TOKENS_SETTING));
			assertEquals(LilypondPlugin.LYBAR_PARSER, settings(target.getRepositoryDir()).getProperty(LilypondPlugin.PARSER_SETTING));
		}
	}

	@Test
	@Timeout(120)
	public void filesOfPluginsWithoutSettingsArePulledWhateverTheLilypondSettings() throws Exception {
		try (EccoService origin = newRepository("settings-sync-origin"); EccoService target = newRepository("settings-sync-target")) {
			commitText(origin);
			setting(target.getRepositoryDir(), LilypondPlugin.MUSICAL_TOKENS_SETTING, "false");
			target.addRemote("origin", origin.getRepositoryDir().getParent().toString(), Remote.Type.LOCAL);

			target.pull("origin");

			assertFalse(target.getRepository().getFeatures().isEmpty());
		}
	}

	@Test
	@Timeout(120)
	public void aPushOfFilesReadDifferentlyIsRefused() throws Exception {
		assumeTrue(LilypondTraceMeasure.readable(), LilypondTraceMeasure.UNREADABLE);
		Path originDir;
		try (EccoService origin = newRepository("settings-sync-origin")) {
			originDir = origin.getRepositoryDir();
			commitText(origin);
		}
		try (EccoService child = newRepository("settings-sync-child")) {
			setting(child.getRepositoryDir(), LilypondPlugin.MUSICAL_TOKENS_SETTING, "false");
			commitLilypond(child);
			child.addRemote("origin", originDir.getParent().toString(), Remote.Type.LOCAL);

			assertRefusedFor(LilypondPlugin.MUSICAL_TOKENS_SETTING, assertThrows(EccoException.class, () -> child.push("origin")));
		}
		try (EccoService origin = open(originDir)) {
			assertEquals(1, origin.getCommits().size(), "nothing merged");
			assertEquals("true", settings(originDir).getProperty(LilypondPlugin.MUSICAL_TOKENS_SETTING));
		}
	}

	@Test
	@Timeout(120)
	public void overTheNetworkAPullIsRefusedAndAPushTakesOverSettings() throws Exception {
		assumeTrue(LilypondTraceMeasure.readable(), LilypondTraceMeasure.UNREADABLE);
		int port;
		try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		EccoService server = newRepository("settings-sync-server");
		Path serverDir = server.getRepositoryDir();
		setting(serverDir, LilypondPlugin.MUSICAL_TOKENS_SETTING, "false");
		commitLilypond(server);
		Thread serverThread = new Thread(() -> server.startServer(port), "test-ecco-server");
		serverThread.start();
		waitForPortOpen(port);
		try (EccoService client = newRepository("settings-sync-client")) {
			client.addRemote("origin", "localhost:" + port, Remote.Type.REMOTE);
			assertRefusedFor(LilypondPlugin.MUSICAL_TOKENS_SETTING, assertThrows(EccoException.class, () -> client.pull("origin")));
			assertTrue(client.getRepository().getFeatures().isEmpty(), "nothing merged");
		} finally {
			server.stopServer();
			serverThread.join(10_000);
			server.close();
		}

		// a server with neither LilyPond settings nor files takes the pushing repository's
		EccoService empty = newRepository("settings-sync-server");
		withoutLilypondSettings(empty.getRepositoryDir());
		Thread emptyThread = new Thread(() -> empty.startServer(port), "test-ecco-server");
		emptyThread.start();
		waitForPortOpen(port);
		try (EccoService client = newRepository("settings-sync-client")) {
			setting(client.getRepositoryDir(), LilypondPlugin.MUSICAL_TOKENS_SETTING, "false");
			commitLilypond(client);
			client.addRemote("origin", "localhost:" + port, Remote.Type.REMOTE);
			client.push("origin");
		} finally {
			empty.stopServer();
			emptyThread.join(10_000);
			empty.close();
		}
		assertEquals("false", settings(empty.getRepositoryDir()).getProperty(LilypondPlugin.MUSICAL_TOKENS_SETTING));
		try (EccoService reopened = open(empty.getRepositoryDir())) {
			assertEquals(1, reopened.getCommits().size(), "the push was merged");
		}
	}

	/**
	 * Known gap #39: a fork over the network took the new-repository settings, not its origin's - the
	 * protocol did not carry them.
	 */
	@Test
	@Timeout(120)
	public void aForkOverTheNetworkTakesItsOriginsSettings() throws Exception {
		int port;
		try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		EccoService server = newRepository("settings-sync-server");
		setting(server.getRepositoryDir(), LilypondPlugin.MUSICAL_TOKENS_SETTING, "false");
		commitText(server);
		Thread serverThread = new Thread(() -> server.startServer(port), "test-ecco-server");
		serverThread.start();
		waitForPortOpen(port);
		Path forkDir = Files.createTempDirectory("settings-sync-fork").resolve(".ecco");
		try (EccoService fork = new EccoService()) {
			fork.setRepositoryDir(forkDir);
			fork.fork("localhost", port);
		} finally {
			server.stopServer();
			serverThread.join(10_000);
			server.close();
		}
		assertEquals(settings(server.getRepositoryDir()), settings(forkDir));
		// forking over the network never worked before: the copy had no entity factory yet
		try (EccoService fork = open(forkDir)) {
			assertEquals(1, fork.getCommits().size());
			assertEquals("localhost:" + port, fork.getRemote("origin").getAddress());
		}
	}

	private static void waitForPortOpen(int port) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < deadline) {
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress("localhost", port), 200);
				return;
			} catch (IOException e) {
				Thread.sleep(50);
			}
		}
		throw new AssertionError("server did not start on port " + port);
	}
}
