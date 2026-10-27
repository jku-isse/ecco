package at.jku.isse.ecco.service;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A repository received over the wire is resolved as a repository opened from disk is: each node
 * holds the one received instance of its artifact, a unique node is its artifact's containing node,
 * each order graph node holds the received instance too, and the artifacts' references (uses/usedBy)
 * hold the received source and target - the receiving side had its own copy of the load's resolving,
 * which set no containing node (the network sync bug of 5ca392d7) and left references unresolved.
 */
public class SyncPayloadTest {

    @Test
    @Timeout(60)
    public void aReceivedRepositoryIsResolvedAsALoadedOne(@TempDir Path tmp) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("origin"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(dir.resolve(".ecco"));
            service.init();
            service.setBaseDir(dir);
            Files.writeString(dir.resolve("a.txt"), "a\n");
            service.commit("a", "A");
            Files.writeString(dir.resolve("a.txt"), "a\nb\n");
            service.commit("a and b", "A, B");

            Repository.Op repository = (Repository.Op) service.getRepository();
            Repository.Op subset = repository.subset(List.of(), repository.getMaxOrder(), repository.getEntityFactory());

            // a reference between two lines held by different associations, as an adapter recording
            // references would make it
            List<Artifact.Op<?>> lines = new ArrayList<>();
            for (Artifact.Op<?> artifact : subset.collectArtifacts())
                if (artifact.getContainingNode() != null && artifact.getContainingNode().getChildren().isEmpty())
                    lines.add(artifact);
            assertTrue(lines.size() >= 2, "two lines: " + lines);
            lines.get(0).addUses(lines.get(1), "calls");

            Repository.Op received = roundTrip(subset);

            Map<String, Artifact.Op<?>> byId = new HashMap<>();
            Set<Artifact.Op<?>> inTrees = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Association.Op association : received.getAssociations())
                collect(association.getRootNode(), byId, inTrees);
            assertEquals(subset.collectArtifacts().size(), inTrees.size(), "one instance per artifact");

            int references = 0;
            for (Artifact.Op<?> artifact : inTrees) {
                if (artifact.getPartialOrderGraph() != null)
                    for (PartialOrderGraph.Node.Op node : artifact.getPartialOrderGraph().collectNodes())
                        if (node.getArtifact() != null)
                            assertSame(byId.get(id(node.getArtifact())), node.getArtifact(), "order graph node of " + artifact);
                for (ArtifactReference.Op reference : artifact.getUses()) {
                    references++;
                    assertSame(artifact, reference.getSource());
                    assertNotNull(reference.getTarget(), "target of a received reference");
                    assertTrue(inTrees.contains(reference.getTarget()), "the received target");
                    assertTrue(reference.getTarget().getUsedBy().stream().anyMatch(r -> r.getSource() == artifact), "its usedBy");
                }
            }
            assertEquals(1, references);

            // and copied by the receiver, as each receive path does next
            received.copy(repository.getEntityFactory());
        }
    }

    private static void collect(Node.Op node, Map<String, Artifact.Op<?>> byId, Set<Artifact.Op<?>> inTrees) {
        Artifact.Op<?> artifact = node.getArtifact();
        if (artifact != null) {
            Artifact.Op<?> known = byId.putIfAbsent(id(artifact), artifact);
            assertTrue(known == null || known == artifact, "one instance for " + id(artifact));
            inTrees.add(artifact);
            if (node.isUnique())
                assertSame(node, artifact.getContainingNode(), "containing node of " + artifact);
        }
        for (Node.Op child : node.getChildren())
            collect(child, byId, inTrees);
    }

    private static String id(Artifact.Op<?> artifact) {
        return ((SerArtifact<?>) artifact).getStorageId();
    }

    /** the subset written and read as pull, push and fork send it */
    private static Repository.Op roundTrip(Repository.Op subset) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
            SyncObjectStreams.writeRepository(oos, subset);
        }
        try (ObjectInputStream ois = SyncObjectStreams.newObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return SyncObjectStreams.readRepository(ois);
        }
    }
}
