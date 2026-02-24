package at.jku.isse.ecco.test;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.*;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import at.jku.isse.ecco.test.util.TestArtifactData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class CollectPogPathsTest {

    @Test
    public void collectingFromEmptyPogReturnsSingleEmptyArrayTest(){
        PartialOrderGraph.Op pog = new SerPartialOrderGraph();
        PartialOrderGraph.Node.Op[][] sequencings = pog.collectNodeSequencings();
        assertEquals(1, sequencings.length);
    }

    @Test
    public void collectingFromPogWithSingleNodeReturnsSingleCorrectPathTest(){
        List<Artifact.Op<?>> artifacts = List.of(A("1"));
        PartialOrderGraph.Op pog = new SerPartialOrderGraph();
        pog.merge(artifacts);

        PartialOrderGraph.Node.Op[][] paths = pog.collectNodeSequencings();
        assertEquals(1, paths.length);
        assertEquals(1, paths[0].length);
        assertEquals(A("1"), paths[0][0].getArtifact());
    }

    @Test
    public void collectingFromPogWithSequentialNodesReturnsSingleCorrectPathTest(){
        List<Artifact.Op<?>> artifacts = Arrays.asList(A("1"), A("2"));
        PartialOrderGraph.Op pog = new SerPartialOrderGraph();
        pog.merge(artifacts);

        PartialOrderGraph.Node.Op[][] paths = pog.collectNodeSequencings();
        assertEquals(1, paths.length);
        assertEquals(2, paths[0].length);
        assertEquals(A("1"), paths[0][0].getArtifact());
        assertEquals(A("2"), paths[0][1].getArtifact());
    }

    @Test
    public void collectingFromPogWithBranchReturnsMultiplePathsTest(){
        List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("2"));
        List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("3"));
        PartialOrderGraph.Op pog = new SerPartialOrderGraph();
        pog.merge(artifacts1);
        pog.merge(artifacts2);

        PartialOrderGraph.Node.Op[][] sequencings = pog.collectNodeSequencings();
        assertEquals(2, sequencings.length);
        assertEquals(3, sequencings[0].length);
        assertEquals(3, sequencings[1].length);
        assertEquals(A("1"), sequencings[0][0].getArtifact());
        assertEquals(A("2"), sequencings[0][1].getArtifact());
        assertEquals(A("3"), sequencings[0][2].getArtifact());
        assertEquals(A("1"), sequencings[1][0].getArtifact());
        assertEquals(A("3"), sequencings[1][1].getArtifact());
        assertEquals(A("2"), sequencings[1][2].getArtifact());
    }

    private Artifact.Op<?> A(String id) {
        return new SerArtifact<>(new TestArtifactData(id));
    }
}
