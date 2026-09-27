package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.tree.SerNode;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every commit re-slices the existing associations against the whole working tree, and
 * Trees.slice() creates new intersection nodes (with new storage ids) even where nothing changed.
 * Artifacts persisted the storage id of their containing node, so every artifact reachable from a
 * touched association was written again on every commit - byte-different only in that id - which
 * dominated commit time (one file per artifact). The containing node is now derived on load from
 * the unique tree node holding each artifact instead of being persisted, and files whose bytes did
 * not change are not rewritten.
 */
public class UnchangedArtifactRewriteTest {

    @Test
    @Timeout(120)
    public void aOneLineCommitDoesNotRewriteUnchangedArtifacts() throws Exception {
        Path workDir = Files.createTempDirectory("unchanged-artifact-rewrite");
        Path repoDir = workDir.resolve(".ecco");
        Path content = Files.createDirectories(workDir.resolve("content"));
        for (int f = 0; f < 20; f++) {
            StringBuilder text = new StringBuilder();
            for (int l = 0; l < 10; l++) text.append("file ").append(f).append(" line ").append(l).append('\n');
            Files.writeString(content.resolve("f" + f + ".txt"), text.toString());
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            service.commit("one", "A");
        }
        Map<String, Long> before = digests(repoDir.resolve("artifacts"));

        Files.writeString(content.resolve("f0.txt"), Files.readString(content.resolve("f0.txt")) + "one more line\n");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            service.setBaseDir(content);
            service.commit("two", "A.2");
        }
        Map<String, Long> after = digests(repoDir.resolve("artifacts"));

        // artifacts are stored in packs, one per write: what the second commit wrote is in its new pack
        long rewritten = after.entrySet().stream().filter(entry -> !before.containsKey(entry.getKey())).mapToLong(Map.Entry::getValue).sum();
        long stored = before.values().stream().mapToLong(Long::longValue).sum();
        assertTrue(stored > 200, "precondition: " + stored + " artifacts");
        assertTrue(rewritten <= 10, "only artifacts that actually changed may be rewritten, but " + rewritten + " of " + stored + " were");
    }

    @Test
    @Timeout(120)
    public void containingNodesAreTheSameAfterReopening() throws Exception {
        Path workDir = Files.createTempDirectory("containing-node-reload");
        Path repoDir = workDir.resolve(".ecco");
        Path content = Files.createDirectories(workDir.resolve("content"));
        Map<String, String> inSession;
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("f.txt"), "c1\nx\ny\n}\n}\nc2\n");
            Files.writeString(content.resolve("g.txt"), "g\n");
            service.commit("a", "A");
            Files.writeString(content.resolve("f.txt"), "c1\ny\nx\n}\nc2\n");
            service.commit("b", "B");
            Files.writeString(content.resolve("f.txt"), "c1\nx\n}\n}\nz\nc2\n");
            Files.delete(content.resolve("g.txt"));
            service.commit("c", "A.2, B");
            inSession = containingNodes(service);
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            assertEquals(inSession, containingNodes(service));
        }
    }

    /** artifact storage id -> storage id of its containing node, for every artifact in every association */
    private static Map<String, String> containingNodes(EccoService service) {
        Map<String, String> result = new HashMap<>();
        for (Association association : service.getRepository().getAssociations()) {
            ArrayDeque<Node> stack = new ArrayDeque<>();
            stack.push(association.getRootNode());
            while (!stack.isEmpty()) {
                Node node = stack.pop();
                Artifact<?> artifact = node.getArtifact();
                if (artifact instanceof SerArtifact<?> serArtifact) {
                    Node containing = serArtifact.getContainingNode();
                    result.put(serArtifact.getStorageId(), containing instanceof SerNode serNode ? serNode.getStorageId() : String.valueOf(containing));
                }
                for (Node child : node.getChildren()) stack.push(child);
            }
        }
        return result;
    }

    /** pack file -> number of artifacts in it (a pack holds a 16 byte digest per artifact) */
    private static Map<String, Long> digests(Path dir) throws Exception {
        Map<String, Long> result = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile());
                     java.io.InputStream in = zip.getInputStream(zip.getEntry("digests"))) {
                    result.put(file.getFileName().toString(), (long) in.readAllBytes().length / 16);
                }
            }
        }
        return result;
    }
}
