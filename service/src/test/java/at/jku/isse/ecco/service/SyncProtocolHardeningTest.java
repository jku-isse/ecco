package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.repository.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The sync protocol where it failed open or stopped (code review of 2026-10-15): a server that could
 * not tell its settings closed the connection, which a client takes for an older server and synced
 * with unchecked; a push was checked against the settings only by the client asking first, and the
 * server merged whatever came; settings taken over were written after the merge was stored, in place;
 * a request nested too deeply to read stopped the server.
 */
public class SyncProtocolHardeningTest {

	private static final Path V2 = LilypondVariantsCommitCheckoutTest.findRepoRoot()
			.resolve("examples").resolve("lilypond_variants").resolve("v2_setup_notes");

	private static EccoService newRepository(String name) throws IOException {
		EccoService service = new EccoService();
		service.setRepositoryDir(Files.createTempDirectory(name).resolve(".ecco"));
		service.init();
		return service;
	}

	private static void commitText(EccoService service) throws IOException {
		Path dir = Files.createTempDirectory("sync-hardening-text");
		Files.writeString(dir.resolve("a.txt"), "a\n");
		service.setBaseDir(dir);
		service.commit("text", "text.1");
	}

	private static void setting(Path repositoryDir, String key, String value) throws IOException {
		Properties settings = new Properties();
		Path file = repositoryDir.resolve(EccoService.SETTINGS_FILE_NAME);
		try (Reader in = Files.newBufferedReader(file)) {
			settings.load(in);
		}
		settings.setProperty(key, value);
		try (Writer out = Files.newBufferedWriter(file)) {
			settings.store(out, null);
		}
	}

	private static final class Server implements AutoCloseable {
		final EccoService service;
		final int port;
		final Thread thread;

		Server(EccoService service) throws Exception {
			this.service = service;
			try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
				this.port = socket.getLocalPort();
			}
			this.thread = new Thread(() -> service.startServer(this.port), "test-ecco-server");
			this.thread.setDaemon(true);
			this.thread.start();
			long deadline = System.currentTimeMillis() + 10_000;
			while (!service.serverRunning() && System.currentTimeMillis() < deadline)
				Thread.sleep(20);
			Thread.sleep(200); // serverRunning is set just before bind()
		}

		@Override
		public void close() throws Exception {
			this.service.stopServer();
			this.thread.join(10_000);
			this.service.close();
		}
	}

	/** A server that cannot read its settings says so; the pull fails instead of merging unchecked. */
	@Test
	@Timeout(120)
	public void aServerThatCannotTellItsSettingsIsNotPulledFromUnchecked() throws Exception {
		EccoService origin = newRepository("sync-hardening-origin");
		commitText(origin);
		Path settings = origin.getRepositoryDir().resolve(EccoService.SETTINGS_FILE_NAME);
		Files.delete(settings);
		Files.createDirectory(settings); // exists, cannot be read
		try (Server server = new Server(origin); EccoService client = newRepository("sync-hardening-client")) {
			client.addRemote("origin", "localhost:" + server.port, Remote.Type.REMOTE);

			EccoException thrown = assertThrows(EccoException.class, () -> client.pull("origin"));

			assertTrue(messages(thrown).contains("Could not tell its settings"), messages(thrown));
			assertTrue(client.getRepository().getFeatures().isEmpty(), "nothing merged");
			assertTrue(server.thread.isAlive(), "the server goes on");
		}
	}

	/**
	 * A push of files read differently is refused by the server itself, also from a client that did
	 * not ask first, and the client is told.
	 */
	@Test
	@Timeout(120)
	public void theServerRefusesAPushOfFilesReadDifferentlyItself() throws Exception {
		assumeTrue(LilypondTraceMeasure.readable(), LilypondTraceMeasure.UNREADABLE);
		try (Server server = new Server(newRepository("sync-hardening-server")); EccoService client = newRepository("sync-hardening-client")) {
			setting(client.getRepositoryDir(), LilypondPlugin.MUSICAL_TOKENS_SETTING, "false");
			client.setBaseDir(V2);
			client.commit("v2", "setup.1, notes.1");
			client.addRemote("origin", "localhost:" + server.port, Remote.Type.REMOTE);

			// what push sends, without asking with MERGE_SETTINGS first
			Repository.Op subset;
			synchronized (client) {
				client.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
				Repository.Op repository = client.repositoryDao.load();
				subset = repository.subset(List.of(), repository.getMaxOrder(), client.entityFactory);
				client.transactionStrategy.end();
			}
			RemoteSyncService sync = new RemoteSyncService(client);
			EccoException thrown = assertThrows(EccoException.class,
					() -> sync.sendPush(client.getRemote("origin"), subset, client.repositorySettings()));

			assertTrue(messages(thrown).contains("read differently") && messages(thrown).contains(LilypondPlugin.MUSICAL_TOKENS_SETTING), messages(thrown));
			assertTrue(server.service.getCommits().isEmpty(), "nothing merged");
			assertTrue(server.thread.isAlive(), "the server goes on");
		}
	}

	/**
	 * A server that answered the settings question is no older ECCO: closing the connection without
	 * answering the push - killed, out of memory while merging - is no success, as it was taken for.
	 */
	@Test
	@Timeout(120)
	public void aPushTheServerDoesNotAnswerIsNoSuccess() throws Exception {
		try (java.net.ServerSocket fake = new java.net.ServerSocket(0, 50, InetAddress.getLoopbackAddress());
			 EccoService client = newRepository("sync-hardening-client")) {
			Thread serving = new Thread(() -> {
				while (!fake.isClosed()) {
					try (Socket socket = fake.accept()) {
						java.io.ObjectInputStream in = new java.io.ObjectInputStream(socket.getInputStream());
						String request = (String) in.readObject();
						if (request.equals("MERGE_SETTINGS")) {
							in.readObject();
							in.readObject();
							in.readObject();
							ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
							out.writeObject("OK");
							out.flush();
						} else if (request.equals("COMMIT_IDS")) {
							// it has none of the client's commits, and no merge commits
							ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
							out.writeObject(new java.util.ArrayList<String>());
							out.writeObject(false);
							out.flush();
						} else {
							socket.getInputStream().readAllBytes(); // the push, never answered
						}
					} catch (Exception ignored) {
					}
				}
			}, "fake-ecco-server");
			serving.setDaemon(true);
			serving.start();
			commitText(client);
			client.addRemote("origin", "localhost:" + fake.getLocalPort(), Remote.Type.REMOTE);

			EccoException thrown = assertThrows(EccoException.class, () -> client.push("origin"));
			assertTrue(messages(thrown).contains("not known whether it merged"), messages(thrown));
		}
	}

	/**
	 * The settings question before a push changes nothing on the server: it wrote the pusher's
	 * settings at once - a push that then failed, or was cancelled, left them.
	 */
	@Test
	@Timeout(120)
	public void askingAboutTheSettingsChangesNothing() throws Exception {
		EccoService serverRepository = newRepository("sync-hardening-server");
		// the server has none of LilyPond's settings (a repository from before them): a push would take them over
		Path serverSettings = serverRepository.getRepositoryDir().resolve(EccoService.SETTINGS_FILE_NAME);
		Properties properties = new Properties();
		try (Reader in = Files.newBufferedReader(serverSettings)) {
			properties.load(in);
		}
		properties.keySet().removeIf(key -> String.valueOf(key).startsWith("lilypond."));
		try (Writer out = Files.newBufferedWriter(serverSettings)) {
			properties.store(out, null);
		}
		try (Server server = new Server(serverRepository); EccoService client = newRepository("sync-hardening-client")) {
			setting(client.getRepositoryDir(), LilypondPlugin.MUSICAL_TOKENS_SETTING, "false");
			client.addRemote("origin", "localhost:" + server.port, Remote.Type.REMOTE);
			Map<String, String> before = server.service.repositorySettings();
			assertFalse(before.containsKey(LilypondPlugin.MUSICAL_TOKENS_SETTING));

			assertTrue(new RemoteSyncService(client).mergeSettingsIntoRemote(client.getRemote("origin"), java.util.Set.of(LilypondPlugin.class.getName())),
					"answered");
			assertEquals(before, server.service.repositorySettings(), "nothing taken over before a push arrives");
		}
	}

	/**
	 * Closing a repository stops its sync server: it went on serving - after another repository was
	 * opened in the same window, that one, without asking whether to accept connections.
	 */
	@Test
	@Timeout(60)
	public void closingTheRepositoryStopsItsServer() throws Exception {
		Server server = new Server(newRepository("sync-hardening-server"));
		assertTrue(server.service.serverRunning());
		server.service.close();
		server.thread.join(10_000);
		assertFalse(server.service.serverRunning());
		assertFalse(server.thread.isAlive(), "the accept loop ended");
	}

	/** A request too deeply nested to read fails alone; the server answers the next one. */
	@Test
	@Timeout(120)
	public void aRequestNestedTooDeeplyDoesNotStopTheServer() throws Exception {
		byte[] deep = deeplyNested(200_000);
		EccoService origin = newRepository("sync-hardening-origin");
		commitText(origin);
		try (Server server = new Server(origin)) {
			try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port)) {
				// the server gives up reading long before the end, and may close meanwhile
				try {
					OutputStream out = socket.getOutputStream();
					out.write(deep);
					out.flush();
					socket.shutdownOutput();
					socket.getInputStream().readAllBytes();
				} catch (IOException ignored) {
				}
			}

			Map<String, String> settings = RemoteSyncService.remoteSettings(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), server.port), "the server");
			assertEquals(origin.repositorySettings(), settings);
			assertTrue(server.thread.isAlive());
		}
	}

	/** "PUSH" and an Object[] in an Object[] ... [depth] deep - written on a thread with a stack for it. */
	private static byte[] deeplyNested(int depth) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		Throwable[] failure = new Throwable[1];
		Thread writer = new Thread(null, () -> {
			try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
				Object[] nested = null;
				for (int i = 0; i < depth; i++)
					nested = new Object[]{nested};
				oos.writeObject("PUSH");
				oos.writeObject(nested);
			} catch (Throwable e) {
				failure[0] = e;
			}
		}, "deep-writer", 2L << 30);
		writer.start();
		writer.join();
		if (failure[0] != null)
			throw new AssertionError(failure[0]);
		return bytes.toByteArray();
	}

	/** Settings are written into a file beside the settings and moved over them: no half-written file, nothing left beside. */
	@Test
	public void settingsAreReplacedAsAWhole() throws Exception {
		Path dir = Files.createTempDirectory("sync-hardening-settings");
		Path file = dir.resolve(EccoService.SETTINGS_FILE_NAME);
		Files.writeString(file, "old=1\n");
		Properties settings = new Properties();
		settings.setProperty("new", "2");

		EccoService.writeSettings(file, settings, null);

		assertEquals(Map.of("new", "2"), EccoService.readSettings(dir));
		try (Stream<Path> files = Files.list(dir)) {
			assertEquals(List.of(file), files.toList());
		}
	}

	private static String messages(Throwable thrown) {
		StringBuilder messages = new StringBuilder();
		for (Throwable t = thrown; t != null; t = t.getCause())
			messages.append(t.getMessage()).append(" / ");
		return messages.toString();
	}
}
