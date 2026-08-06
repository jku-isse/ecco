package at.jku.isse.ecco.adapter.java.test;

import at.jku.isse.ecco.adapter.java.JavaReader;
import at.jku.isse.ecco.adapter.java.JavaWriter;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.Assert;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class JavaIntegrityTestHelper {


    public static void integrityTest(String... files) {
        integrityTestInternal(NewJavaReaderTests.ressourceRoot, NewJavaWriterTest.outRoot, files);
    }

    public static void integrityTestJdk(String... files) {
        integrityTestInternal(NewJavaReaderTests.ressourceRoot.resolve("jdk"), NewJavaWriterTest.outRoot.resolve("jdk"), files);
    }

    public static void integrityTestSpring(String... files) {
        integrityTestInternal(NewJavaReaderTests.ressourceRoot.resolve("spring"), NewJavaWriterTest.outRoot.resolve("spring"), files);
    }

    private static void integrityTestInternal(Path inRoot, Path outRoot, String... files) {
        if (files == null || files.length == 0)
            throw new IllegalStateException("Remember to pass filenames!!");
        JavaReader reader = new JavaReader(new MemEntityFactory());
        final Set<? extends Node> originalSet = reader.read(inRoot, Arrays.stream(files).map(Paths::get).toArray(Path[]::new));
        final List<Node> read1 = Collections.unmodifiableList(new ArrayList<>(originalSet));

        JavaWriter writer = new JavaWriter();
        writer.write(outRoot, (Set<Node>) originalSet);

        final Set<Node.Op> secondRead = reader.read(outRoot, Arrays.stream(files).map(Paths::get).toArray(Path[]::new));
        final List<Node> read2 = Collections.unmodifiableList(new ArrayList<>(secondRead));

        Assert.assertTrue("Original set is empty, no files have been parsed", originalSet.size() > 0);

        Assert.assertTrue("Second set is empty, no files have been parsed", secondRead.size() > 0);

        Assert.assertTrue(areListsEqual(read1, read2));
    }

    private static boolean areListsEqual(List<? extends Node> original, List<? extends Node> changed) {
        if (original == changed)
            throw new IllegalStateException("The two lists must not be the same! Probably this method has been called incorrectly!");
        Assert.assertEquals("List of children are not equal." + System.lineSeparator() + "Original: " + original + System.lineSeparator() + " Changed: " + changed, original.size(), changed.size());
        if (original.size() != changed.size())
            return false;
        final int length = original.size();
        for (int i = 0; i < length; i++) {
            boolean curNodesEqual = areNodesEqual(original.get(i), changed.get(i));
            if (!curNodesEqual)
                throw new IllegalStateException("Assert Missing");
        }
        return true;
    }

    private static boolean areNodesEqual(Node a, Node b) {
        ArtifactData dataA = a.getArtifact().getData(), datab = b.getArtifact().getData();
        boolean thisSame = dataA != null && dataA.equals(datab);
        Assert.assertTrue("Following nodes are not equal: Original: " + dataA + " | After read: " + datab, thisSame);

        final List<? extends Node> childsA = a.getChildren(), childsB = b.getChildren();
        return areListsEqual(childsA, childsB);
    }
}
