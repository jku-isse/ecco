package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fork, pull and push over the network (a real loopback socket) of a file whose lines several
 * associations hold. subset() copies each association on its own, so a copied file's order graph
 * pointed at the source repository's line artifacts for the lines another association holds; a local
 * merge() redirected them, but over the socket only the artifacts in the trees were sent and the
 * receiver failed with "Could not resolve POG node artifact ... among the artifacts received" (push:
 * the server failed the same way and nothing was merged). Each result is reopened and every
 * configuration checked out, and compared with a local fork of the same selection.
 */
public class NetworkSyncSharedFileTest {

    /** commit 1 "A": a.txt "a"; commit 2 "A, B": a.txt "a\nb" */
    private static Path twoFeatures(Path tmp, String name) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve(name));
        try (EccoService service = init(dir)) {
            Files.writeString(dir.resolve("a.txt"), "a\n");
            service.commit("a", "A");
            Files.writeString(dir.resolve("a.txt"), "a\nb\n");
            service.commit("a and b", "A, B");
        }
        return dir;
    }

    /** twoFeatures, then commit 3 "A', B, C": a.txt "a2\nb\nc"; the revisions as "A.id" */
    private record ThreeFeatures(Path dir, String a1, String a2, String b1, String c1) {
    }

    private static ThreeFeatures threeFeatures(Path tmp, String name) throws Exception {
        Path dir = twoFeatures(tmp, name);
        try (EccoService service = open(dir)) {
            String a1 = latestRevision(service, "A");
            Files.writeString(dir.resolve("a.txt"), "a2\nb\nc\n");
            service.commit("a2, b and c", "A', B, C");
            return new ThreeFeatures(dir, a1, latestRevision(service, "A"), latestRevision(service, "B"), latestRevision(service, "C"));
        }
    }

    private static String latestRevision(EccoService service, String feature) {
        return service.getRepository().getFeatures().stream().filter(f -> f.getName().equals(feature))
                .findFirst().orElseThrow().getLatestRevision().toString();
    }

    private static final Map<String, String> TWO_FEATURES = Map.of("A", "a\n", "A, B", "a\nb\n");

    @Test
    @Timeout(120)
    public void pullOverTheNetwork(@TempDir Path tmp) throws Exception {
        Path origin = twoFeatures(tmp, "origin");
        Path pulled = Files.createDirectories(tmp.resolve("pulled"));
        withServer(origin, port -> {
            try (EccoService service = init(pulled)) {
                service.addRemote("origin", "localhost:" + port, Remote.Type.REMOTE);
                service.pull("origin");
            }
        });
        assertCheckouts(pulled, TWO_FEATURES, tmp.resolve("co-pulled"));
    }

    @Test
    @Timeout(120)
    public void forkOverTheNetwork(@TempDir Path tmp) throws Exception {
        Path origin = twoFeatures(tmp, "origin");
        Path forked = Files.createDirectories(tmp.resolve("forked"));
        withServer(origin, port -> {
            try (EccoService service = new EccoService()) {
                service.setRepositoryDir(forked.resolve(".ecco"));
                service.fork("localhost", port);
            }
        });
        assertCheckouts(forked, TWO_FEATURES, tmp.resolve("co-forked"));
    }

    @Test
    @Timeout(120)
    public void pushOverTheNetwork(@TempDir Path tmp) throws Exception {
        Path client = twoFeatures(tmp, "client");
        Path server = Files.createDirectories(tmp.resolve("server"));
        try (EccoService service = init(server)) {
            // empty; closed again, the server opens it itself
        }
        withServer(server, port -> {
            try (EccoService service = open(client)) {
                service.addRemote("server", "localhost:" + port, Remote.Type.REMOTE);
                service.push("server");
            }
        });
        assertCheckouts(server, TWO_FEATURES, tmp.resolve("co-server"));
    }

    @Test
    @Timeout(180)
    public void pullAndForkOverTheNetworkWithExcludedRevisionsMatchLocalForks(@TempDir Path tmp) throws Exception {
        ThreeFeatures history = threeFeatures(tmp, "origin");
        Path origin = history.dir();
        String a1 = history.a1(), a2 = history.a2(), b1 = history.b1(), c1 = history.c1();

        // pull without the last commit's feature C
        Path pulled = Files.createDirectories(tmp.resolve("pulled"));
        // fork without A's first revision
        Path forked = Files.createDirectories(tmp.resolve("forked"));
        // push without C, into an empty server
        Path server = Files.createDirectories(tmp.resolve("server"));
        withServer(origin, port -> {
            try (EccoService service = init(pulled)) {
                service.addRemote("origin", "localhost:" + port, Remote.Type.REMOTE);
                service.pull("origin", c1);
            }
            try (EccoService service = new EccoService()) {
                service.setRepositoryDir(forked.resolve(".ecco"));
                service.fork("localhost", port, a1);
            }
        });
        try (EccoService service = init(server)) {
            // empty
        }
        withServer(server, port -> {
            try (EccoService service = open(origin)) {
                service.addRemote("server", "localhost:" + port, Remote.Type.REMOTE);
                service.push("server", c1);
            }
        });

        Path localWithoutC = localFork(origin, tmp.resolve("local-without-c"), c1);
        Path localWithoutA1 = localFork(origin, tmp.resolve("local-without-a1"), a1);

        Map<String, String> withoutC = new LinkedHashMap<>();
        withoutC.put(a1, "a\n");
        withoutC.put(a1 + ", " + b1, "a\nb\n");
        withoutC.put(a2 + ", " + b1, checkoutOf(localWithoutC, a2 + ", " + b1, tmp.resolve("local-c-a2b1")));
        assertCheckouts(pulled, withoutC, tmp.resolve("co-pulled"));
        assertCheckouts(server, withoutC, tmp.resolve("co-server"));
        try (EccoService service = open(pulled)) {
            assertEquals(Set.of("A", "B"), revisions(service).keySet());
        }

        Map<String, String> withoutA1 = new LinkedHashMap<>();
        withoutA1.put(a2 + ", " + b1 + ", " + c1, "a2\nb\nc\n");
        withoutA1.put(a2 + ", " + b1, checkoutOf(localWithoutA1, a2 + ", " + b1, tmp.resolve("local-a1-a2b1")));
        withoutA1.put(b1 + ", " + c1, checkoutOf(localWithoutA1, b1 + ", " + c1, tmp.resolve("local-a1-b1c1")));
        assertCheckouts(forked, withoutA1, tmp.resolve("co-forked"));
        try (EccoService service = open(forked)) {
            assertEquals(1, revisions(service).get("A").size());
        }
    }

    // helpers

    private interface WithPort {
        void run(int port) throws Exception;
    }

    /** serves the repository in dir while running the body, then stops and closes it */
    private static void withServer(Path dir, WithPort body) throws Exception {
        int port;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        EccoService server = open(dir);
        Thread thread = new Thread(() -> server.startServer(port), "test-ecco-server");
        thread.start();
        try {
            waitForPortOpen(port, 10_000);
            body.run(port);
        } finally {
            server.stopServer();
            thread.join(10_000);
            server.close();
        }
    }

    private static Path localFork(Path origin, Path dir, String excluded) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(dir.resolve(".ecco"));
            service.fork(origin, excluded);
        }
        return dir;
    }

    /** reopens the repository and checks out every configuration, each into its own folder */
    private static void assertCheckouts(Path repository, Map<String, String> expectedATxt, Path checkouts) throws Exception {
        int i = 0;
        for (Map.Entry<String, String> entry : expectedATxt.entrySet())
            assertEquals(entry.getValue(), checkoutOf(repository, entry.getKey(), checkouts.resolve("" + i++)), "a.txt of " + entry.getKey());
    }

    private static String checkoutOf(Path repository, String configuration, Path dir) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = open(repository)) {
            service.setBaseDir(dir);
            service.checkout(configuration);
        }
        return Files.readString(dir.resolve("a.txt"));
    }

    private static EccoService init(Path dir) {
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.init();
        service.setBaseDir(dir);
        return service;
    }

    private static EccoService open(Path dir) {
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.open();
        service.setBaseDir(dir);
        return service;
    }

    /** feature name -> its revision ids */
    private static Map<String, Set<String>> revisions(EccoService service) {
        Map<String, Set<String>> result = new TreeMap<>();
        for (Feature feature : service.getRepository().getFeatures())
            result.put(feature.getName(), feature.getRevisions().stream().map(FeatureRevision::getId).collect(Collectors.toCollection(TreeSet::new)));
        return result;
    }

    private static void waitForPortOpen(int port, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("localhost", port), 200);
                return;
            } catch (IOException e) {
                Thread.sleep(50);
            }
        }
        throw new AssertionError("Server did not start listening on port " + port + " within " + timeoutMillis + "ms");
    }
}
