package at.jku.isse.ecco.test;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.*;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.pog.MemPartialOrderGraph;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class CollectPogPathsTest {

    @Test
    public void collectingFromEmptyPogReturnsSingleEmptyArrayTest(){
        PartialOrderGraph.Op pog = new MemPartialOrderGraph();
        PartialOrderGraph.Node.Op[][] sequencings = pog.collectNodeSequencings();
        assertEquals(1, sequencings.length);
    }

    @Test
    public void collectingFromPogWithSingleNodeReturnsSingleCorrectPathTest(){
        List<Artifact.Op<?>> artifacts = List.of(A("1"));
        PartialOrderGraph.Op pog = new MemPartialOrderGraph();
        pog.merge(artifacts);

        PartialOrderGraph.Node.Op[][] paths = pog.collectNodeSequencings();
        assertEquals(1, paths.length);
        assertEquals(1, paths[0].length);
        assertEquals(A("1"), paths[0][0].getArtifact());
    }

    @Test
    public void collectingFromPogWithSequentialNodesReturnsSingleCorrectPathTest(){
        List<Artifact.Op<?>> artifacts = Arrays.asList(A("1"), A("2"));
        PartialOrderGraph.Op pog = new MemPartialOrderGraph();
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
        PartialOrderGraph.Op pog = new MemPartialOrderGraph();
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

    // todo: check starting here
    @Test
    public void collectingFromPogWithMultipleBranchReturnsMultiplePathsTest(){
        List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("a"), A("x"));
        List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("a"), A("y"));
        List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("b"), A("i"));
        List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("1"), A("b"), A("j"));
        PartialOrderGraph.Op pog = new MemPartialOrderGraph();
        pog.merge(artifacts1);
        pog.merge(artifacts2);
        pog.merge(artifacts3);
        pog.merge(artifacts4);

        PartialOrderGraph.Node.Op[][] sequencings = pog.collectNodeSequencings();
        assertEquals(4, sequencings.length);
        assertEquals(3, sequencings[0].length);
        assertEquals(3, sequencings[1].length);
        assertEquals(3, sequencings[2].length);
        assertEquals(3, sequencings[3].length);
        assertEquals(A("1"), sequencings[0][0].getArtifact());
        assertEquals(A("a"), sequencings[0][1].getArtifact());
        assertEquals(A("x"), sequencings[0][2].getArtifact());
        assertEquals(A("1"), sequencings[1][0].getArtifact());
        assertEquals(A("a"), sequencings[1][1].getArtifact());
        assertEquals(A("y"), sequencings[1][2].getArtifact());
        assertEquals(A("1"), sequencings[2][0].getArtifact());
        assertEquals(A("b"), sequencings[2][1].getArtifact());
        assertEquals(A("i"), sequencings[2][2].getArtifact());
        assertEquals(A("1"), sequencings[3][0].getArtifact());
        assertEquals(A("b"), sequencings[3][1].getArtifact());
        assertEquals(A("j"), sequencings[3][2].getArtifact());
    }

    @Test
    public void collectingFromPogWithFourWayBranchReturnsMultiplePathsTest(){
        List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("a"), A("i"));
        List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("a"), A("j"));
        List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("a"), A("k"));
        List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("1"), A("a"), A("l"));
        PartialOrderGraph.Op pog = new MemPartialOrderGraph();
        pog.merge(artifacts1);
        pog.merge(artifacts2);
        pog.merge(artifacts3);
        pog.merge(artifacts4);

        PartialOrderGraph.Node.Op[][] paths = pog.collectNodeSequencings();
        assertEquals(4, paths.length);
        assertEquals(3, paths[0].length);
        assertEquals(3, paths[1].length);
        assertEquals(3, paths[2].length);
        assertEquals(3, paths[3].length);
        assertEquals(A("1"), paths[0][0].getArtifact());
        assertEquals(A("a"), paths[0][1].getArtifact());
        assertEquals(A("i"), paths[0][2].getArtifact());
        assertEquals(A("1"), paths[1][0].getArtifact());
        assertEquals(A("a"), paths[1][1].getArtifact());
        assertEquals(A("j"), paths[1][2].getArtifact());
        assertEquals(A("1"), paths[2][0].getArtifact());
        assertEquals(A("a"), paths[2][1].getArtifact());
        assertEquals(A("k"), paths[2][2].getArtifact());
        assertEquals(A("1"), paths[3][0].getArtifact());
        assertEquals(A("a"), paths[3][1].getArtifact());
        assertEquals(A("l"), paths[3][2].getArtifact());
    }

    // multiple branches after each other
    @Test
    public void collectingFromPogWithMultipleBranchAfterEachOtherReturnsMultiplePathsTest(){
        List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("a"), A("x"));
        List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("a"), A("y"));
        List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("b"), A("x"));
        List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("1"), A("b"), A("y"));
        PartialOrderGraph.Op pog = new MemPartialOrderGraph();
        pog.merge(artifacts1);
        pog.merge(artifacts2);
        pog.merge(artifacts3);
        pog.merge(artifacts4);

        PartialOrderGraph.Node.Op[][] paths = pog.collectNodeSequencings();
        assertEquals(4, paths.length);
        assertEquals(3, paths[0].length);
        assertEquals(3, paths[1].length);
        assertEquals(3, paths[2].length);
        assertEquals(3, paths[3].length);
        assertEquals(A("1"), paths[0][0].getArtifact());
        assertEquals(A("a"), paths[0][1].getArtifact());
        assertEquals(A("x"), paths[0][2].getArtifact());
        assertEquals(A("1"), paths[1][0].getArtifact());
        assertEquals(A("a"), paths[1][1].getArtifact());
        assertEquals(A("y"), paths[1][2].getArtifact());
        assertEquals(A("1"), paths[2][0].getArtifact());
        assertEquals(A("b"), paths[2][1].getArtifact());
        assertEquals(A("x"), paths[2][2].getArtifact());
        assertEquals(A("1"), paths[3][0].getArtifact());
        assertEquals(A("b"), paths[3][1].getArtifact());
        assertEquals(A("y"), paths[3][2].getArtifact());
    }

    private Artifact.Op<?> A(String id) {
        return new MemArtifact<>(new TestArtifactData(id));
    }

    private Artifact.Op<?> A(String id, int number) {
        Artifact.Op<?> artifact = new MemArtifact<>(new TestArtifactData(id));
        artifact.setSequenceNumber(number);
        return artifact;
    }
}
