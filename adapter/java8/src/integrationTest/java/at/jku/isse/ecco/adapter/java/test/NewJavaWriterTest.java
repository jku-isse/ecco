package at.jku.isse.ecco.adapter.java.test;

import at.jku.isse.ecco.adapter.java.JavaReader;
import at.jku.isse.ecco.adapter.java.JavaWriter;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Set;

public class NewJavaWriterTest {

    public static final Path outRoot = NewJavaReaderTests.ressourceRoot.resolve("../newOutput");

    @Test
    public void simpleWriteTest() {
        writeTest("Simple.java");
    }

    @Test
    public void writeSwitchCaseTest() {
        writeTest("SwitchCase.java");
    }

    @Test
    public void writeFiles1() {
        writeTest("JavaTreeArtifactData.java", "LoginRequired.java", "TestFile.java");
    }

    @Test
    public void writeFiles2() {
        writeTest("SimulationConfig_v5.java", "WindowSizes.java");
    }

    @Test
    public void writeLoopTest() {
        writeTest("Looptest.java");
    }

    @Test
    public void writeSmallRegressiontestTest() {
        writeTest("ArrayList.java");
    }

    @Test
    public void writeJdkTest() {
        writeJdkTest("ArrayList.java", "BatchUpdateException.java", "List.java", "Math.java", "Proxy.java");
    }

    private void writeTest(String... filename) {
        JavaReader javaReader = new JavaReader(new MemEntityFactory());
        final Set<? extends Node> nodes = NewJavaReaderTests.read(javaReader, filename);
        JavaWriter javaWriter = new JavaWriter();
        javaWriter.write(outRoot, ((Set<Node>) nodes));
    }

    private void writeJdkTest(String... filename) {
        Path outRoot = NewJavaWriterTest.outRoot.resolve("jdk");
        JavaReader javaReader = new JavaReader(new MemEntityFactory());
        final Set<? extends Node> nodes = NewJavaReaderTests.readJDK(javaReader, filename);
        JavaWriter javaWriter = new JavaWriter();
        javaWriter.write(outRoot, ((Set<Node>) nodes));
    }
}
