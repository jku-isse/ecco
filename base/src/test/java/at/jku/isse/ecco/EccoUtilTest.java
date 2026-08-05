package at.jku.isse.ecco;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.test.util.TestArtifactData;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class EccoUtilTest {

    private final EntityFactory ef = new SerEntityFactory();

    @Test
    public void deepCopyFeaturesCopiesNameDescriptionAndRevisions() {
        Feature original = ef.createFeature(UUID.randomUUID().toString(), "A");
        original.setDescription("the A feature");
        FeatureRevision revision = original.addRevision(UUID.randomUUID().toString());
        revision.setDescription("first revision");

        Collection<Feature> copies = EccoUtil.deepCopyFeatures(java.util.List.of(original), ef);

        assertEquals(1, copies.size());
        Feature copy = copies.iterator().next();
        assertNotSame(original, copy);
        assertEquals(original.getId(), copy.getId());
        assertEquals("A", copy.getName());
        assertEquals("the A feature", copy.getDescription());
        assertEquals(1, copy.getRevisions().size());
        FeatureRevision copiedRevision = copy.getRevisions().iterator().next();
        assertEquals(revision.getId(), copiedRevision.getId());
        assertEquals("first revision", copiedRevision.getDescription());
    }

    @Test
    public void deepCopyTreeCopiesStructureAndArtifactData() {
        RootNode.Op root = ef.createRootNode();
        Node.Op child = ef.createNode(new TestArtifactData("child"));
        root.addChild(child);

        Node.Op copy = EccoUtil.deepCopyTree(root, ef);

        assertNotSame(root, copy);
        assertEquals(1, copy.getChildren().size());
        Node.Op copiedChild = copy.getChildren().iterator().next();
        assertNotSame(child, copiedChild);
        assertEquals("child", copiedChild.getArtifact().getData().toString());
    }

    @Test
    public void deepCopyTreeReplacesASharedArtifactWithTheSameCopyEverywhere() {
        // Two nodes in the source tree deliberately share one Artifact.Op instance by identity (as
        // Trees.slice() does for genuinely shared content) - deepCopyTreeRec's
        // hasReplacingArtifact()/setReplacingArtifact() caching (EccoUtil.java:71-80) is what's
        // supposed to keep that sharing intact in the copy rather than producing two independent
        // copies of the same artifact - see pog-mismatch-real-cause-duplicate-storageid in project
        // history for what goes wrong when an artifact ends up duplicated like that.
        Artifact.Op<TestArtifactData> sharedArtifact = ef.createArtifact(new TestArtifactData("shared"));
        RootNode.Op root = ef.createRootNode();
        Node.Op child1 = ef.createNode();
        child1.setArtifact(sharedArtifact);
        Node.Op child2 = ef.createNode();
        child2.setArtifact(sharedArtifact);
        root.addChild(child1);
        root.addChild(child2);

        Node.Op copy = EccoUtil.deepCopyTree(root, ef);

        Node.Op[] copiedChildren = copy.getChildren().toArray(new Node.Op[0]);
        assertEquals(2, copiedChildren.length);
        assertSame(copiedChildren[0].getArtifact(), copiedChildren[1].getArtifact(), "both copied nodes should point at the same copied artifact instance");
    }

    @Test
    public void getSHAIsDeterministicAndContentSensitive() throws IOException {
        Path fileA1 = Files.createTempFile("ecco-util-sha", ".txt");
        Files.writeString(fileA1, "hello\n");
        Path fileA2 = Files.createTempFile("ecco-util-sha", ".txt");
        Files.writeString(fileA2, "hello\n");
        Path fileB = Files.createTempFile("ecco-util-sha", ".txt");
        Files.writeString(fileB, "goodbye\n");

        String shaA1 = EccoUtil.getSHA(fileA1);
        String shaA2 = EccoUtil.getSHA(fileA2);
        String shaB = EccoUtil.getSHA(fileB);

        assertEquals(shaA1, shaA2, "identical content should hash identically");
        assertNotEquals(shaA1, shaB, "different content should hash differently");
    }

    @Test
    public void getSHAThrowsForAMissingFile() {
        assertThrows(EccoException.class, () -> EccoUtil.getSHA(Path.of("/does/not/exist/" + UUID.randomUUID())));
    }
}
