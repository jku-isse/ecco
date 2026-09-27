package at.jku.isse.ecco.storage.ser.pog;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SerPartialOrderGraph.writeObject()/SerPartialOrderGraphNode.prepareSerialization() told the
 * head/tail sentinels apart from real nodes by "artifact == null" - the same fragile test the
 * reload side had already replaced with identity checks (the artifact reference is transient and
 * only filled in by a post-load resolution pass). A real node whose artifact wasn't resolved at
 * write time was therefore dropped from the written graph together with its edges: the ordering
 * silently lost nodes. Head and tail are now identified by identity.
 */
public class SerPartialOrderGraphUnresolvedNodeSerializationTest {

    record TestArtifactData(String id) implements ArtifactData {
        private static final long serialVersionUID = 1L;
    }

    @Test
    public void aNodeWhoseArtifactIsNotResolvedIsStillWritten() throws Exception {
        SerPartialOrderGraph pog = new SerPartialOrderGraph();
        pog.merge(List.<Artifact.Op<?>>of(
                new SerArtifact<>(new TestArtifactData("a")),
                new SerArtifact<>(new TestArtifactData("b")),
                new SerArtifact<>(new TestArtifactData("c"))));
        assertEquals(5, pog.collectNodes().size(), "precondition: head, a, b, c, tail");

        // as if loaded but not (yet) resolved: artifactId set, transient artifact reference empty
        SerPartialOrderGraphNode b = (SerPartialOrderGraphNode) pog.collectNodes().stream()
                .filter(n -> n.getArtifact() != null && n.getArtifact().getData().toString().contains("b"))
                .findFirst().orElseThrow();
        b.resolveArtifact(null);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(pog);
        }
        PartialOrderGraph.Op reloaded;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            reloaded = (PartialOrderGraph.Op) in.readObject();
        }

        assertEquals(5, reloaded.collectNodes().size(), "no node may be dropped from the written graph");
    }
}
