package at.jku.isse.ecco.adapter.java.test;

import at.jku.isse.ecco.adapter.java.JavaReader;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;

public class NewJavaReaderTests {

    @Test
    public void readTest() {
        JavaReader javaReader = new JavaReader(new MemEntityFactory());
        read(javaReader, "SimulationConfig_v5.java").forEach(this::print);
    }

    @Test
    public void simpleRead1() throws Exception {
        JavaReader javaReader = new JavaReader(new MemEntityFactory());
        read(javaReader, "Simple.java").forEach(this::print);

    }

    @Test
    public void readWindoSizes() {
        JavaReader javaReader = new JavaReader(new MemEntityFactory());
        read(javaReader, "WindowSizes.java").forEach(this::print);
    }

    @Test
    public void readLooptest() {
        JavaReader javaReader = new JavaReader(new MemEntityFactory());
        read(javaReader, "Looptest.java").forEach(this::print);
    }

    @Test
    public void readJavaTreeArtifactData() {
        JavaReader javaReader = new JavaReader(new MemEntityFactory());
        read(javaReader, "JavaTreeArtifactData.java").forEach(this::print);
    }

    @Test
    public void readSwitchCase() {
        JavaReader javaReader = new JavaReader(new MemEntityFactory());
        read(javaReader, "SwitchCase.java").forEach(this::print);
    }

    @Test
    public void readLoginRequired() {
        JavaReader javaReader = new JavaReader(new MemEntityFactory());
        read(javaReader, "LoginRequired.java").forEach(this::print);
    }

    @Test
    public void jdkRead() {
        JavaReader javaReader = new JavaReader(new MemEntityFactory());
        readJDK(javaReader,
                "ArrayList.java", "BatchUpdateException.java", "List.java", "Math.java", "Proxy.java"
        ).forEach(this::print);
    }

    private void print(Node.Op nodeOp) {
        System.out.println(nodeOp);
        for (Node.Op child : nodeOp.getChildren())
            print(child);
    }

    public static final Path ressourceRoot = Paths.get("src/integrationTest/data/newInput");

    public static Set<Node.Op> read(JavaReader reader, String... files) {
        return reader.read(ressourceRoot, Arrays.stream(files).map(Paths::get).toArray(Path[]::new));
    }

    public static Set<Node.Op> readJDK(JavaReader reader, String... files) {
        return reader.read(ressourceRoot.resolve("jdk"), Arrays.stream(files).map(Paths::get).toArray(Path[]::new));
    }

}
