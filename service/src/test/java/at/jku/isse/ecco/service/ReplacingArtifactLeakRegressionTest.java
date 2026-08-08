package at.jku.isse.ecco.service;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.repository.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * fork(Path) B from A, commit something new locally on B, then push B's repository back into A
 * (real cross-process PUSH scenario, replicated here without sockets for speed) used to fail with
 * one of two exceptions, both thrown from deep inside Trees.slice()/PartialOrderGraph.merge():
 * "Replacing artifact should not have a replacing artifact itself!" or "POG node count mismatch!".
 * Root-caused to two compounding bugs, both now fixed:
 * <p>
 * 1. SerRepository.resolveArtifacts() (the companion-payload resolution for a Repository.Op sent
 * whole over a raw socket, per Repository.Op#restoreAssociations/#collectArtifacts/#resolveArtifacts)
 * only resolved tree-node artifact references (SerNode.artifactId), never a PartialOrderGraph's own
 * node artifact references (SerPartialOrderGraphNode.artifactId) - unlike the normal DAO load path
 * (SerTransactionStrategy.resolveCrossAssociationReferences()/resolvePartialOrderGraph()), which
 * correctly handles both. Left unresolved, an ordered artifact's PartialOrderGraph node had a real,
 * already-assigned sequence number but a null getArtifact(), which PartialOrderGraph.merge()'s node-
 * matching (which filters out null-artifact nodes) then miscounted as unmatched/missing.
 * <p>
 * 2. Artifact.replacingArtifact (see EccoUtil.deepCopyTreeRec()) is reused for two different,
 * temporally-overlapping purposes on the same object - a "have I already copied this artifact" cache
 * scoped to one deepCopyTree() call, and Trees.slice()'s "this was matched/replaced by that" marker -
 * and was never cleared after either use. A push/pull round-trip chains multiple deepCopyTree()
 * calls (client subset, server re-copy, merge()'s own per-association copy) plus Trees.slice()'s own
 * usage, all sharing artifact objects across calls whenever content (e.g. a shared/non-unique
 * skeleton node, or content round-tripped back to its origin) repeats between them - so state from an
 * earlier, unrelated copy left a later one either wrongly reusing a stale cached copy, or tripping
 * the "replacing artifact should not itself have a replacing artifact" guard. Fixed by clearing
 * replacingArtifact at the end of each deepCopyTree() call, once fully consumed.
 */
public class ReplacingArtifactLeakRegressionTest {

    @Test
    @Timeout(30)
    public void forkingFromARepoWithTwoUnrelatedAssociationsWorks() throws IOException {
        Path workDir = Files.createTempDirectory("replacing-artifact-regression-1");
        Path repoADir = workDir.resolve("repoA").resolve(".ecco");
        Path repoBDir = workDir.resolve("repoB").resolve(".ecco");
        Files.createDirectories(repoADir.getParent());
        Files.createDirectories(repoBDir.getParent());

        EccoService serviceA = new EccoService();
        serviceA.setRepositoryDir(repoADir);
        serviceA.init();
        commitFeature(serviceA, workDir, "core", "Core");
        commitFeature(serviceA, workDir, "extra", "Extra");

        EccoService serviceB = new EccoService();
        serviceB.setRepositoryDir(repoBDir);
        serviceB.fork(repoADir);

        Collection<String> featureNames = serviceB.getRepository().getFeatures().stream()
                .map(Feature::getName).collect(Collectors.toList());
        assertTrue(featureNames.containsAll(java.util.List.of("Core", "Extra")));

        serviceA.close();
        serviceB.close();
    }

    @Test
    @Timeout(30)
    public void forkThenLocalCommitThenPushBackIntoOriginRoundTripsWithoutCorruption() throws Exception {
        Path workDir = Files.createTempDirectory("replacing-artifact-regression-2");
        Path repoADir = workDir.resolve("repoA").resolve(".ecco");
        Path repoBDir = workDir.resolve("repoB").resolve(".ecco");
        Files.createDirectories(repoADir.getParent());
        Files.createDirectories(repoBDir.getParent());

        EccoService serviceA = new EccoService();
        serviceA.setRepositoryDir(repoADir);
        serviceA.init();
        commitFeature(serviceA, workDir, "core", "Core");

        EccoService serviceB = new EccoService();
        serviceB.setRepositoryDir(repoBDir);
        serviceB.fork(repoADir);
        commitFeature(serviceB, workDir, "extra", "Extra");

        Repository.Op repoAOp = (Repository.Op) serviceA.getRepository();
        Repository.Op repoBOp = (Repository.Op) serviceB.getRepository();

        // Precisely mirrors RemoteSyncService's PUSH sequence, minus the socket:
        // 1. client (B) subsets its own repo with its OWN entity factory
        Repository.Op subsetRepository = repoBOp.subset(new ArrayList<>(), repoBOp.getMaxOrder(), repoBOp.getEntityFactory());
        // 2. it travels as a real Java serialization round-trip (clears transient fields like
        //    Artifact.replacingArtifact, unlike in-process reference reuse), with its companion
        //    association/artifact payloads (see Repository.Op#restoreAssociations/#collectArtifacts
        //    /#resolveArtifacts -- ordinary serialization alone loses these).
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
            oos.writeObject(subsetRepository);
            oos.writeObject(new ArrayList<>(subsetRepository.getAssociations()));
            oos.writeObject(new ArrayList<>(subsetRepository.collectArtifacts()));
        }
        Repository.Op deserializedSubsetRepository;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            deserializedSubsetRepository = (Repository.Op) ois.readObject();
            @SuppressWarnings("unchecked")
            Collection<Association.Op> pushedAssociations = (Collection<Association.Op>) ois.readObject();
            deserializedSubsetRepository.restoreAssociations(pushedAssociations);
            @SuppressWarnings("unchecked")
            Collection<Artifact.Op<?>> pushedArtifacts = (Collection<Artifact.Op<?>>) ois.readObject();
            deserializedSubsetRepository.resolveArtifacts(pushedArtifacts);
        }
        // 3. server (A) re-copies the deserialized repo with ITS OWN entity factory
        Repository.Op copiedRepository = deserializedSubsetRepository.copy(repoAOp.getEntityFactory());
        // 4. server merges that into its own freshly-loaded repo
        repoAOp.merge(copiedRepository);

        Collection<String> featureNames = repoAOp.getFeatures().stream().map(Feature::getName).collect(Collectors.toList());
        assertTrue(featureNames.containsAll(java.util.List.of("Core", "Extra")),
                "origin must end up with both the content it already had and the freshly-committed content pushed back into it");

        serviceA.close();
        serviceB.close();
    }

    private static void commitFeature(EccoService service, Path workDir, String dirName, String featureName) throws IOException {
        Path p = workDir.resolve(dirName);
        Files.createDirectories(p);
        Files.writeString(p.resolve(dirName + ".txt"), dirName + "\n");
        service.setBaseDir(p);
        service.commit("commit " + featureName, featureName);
    }
}
