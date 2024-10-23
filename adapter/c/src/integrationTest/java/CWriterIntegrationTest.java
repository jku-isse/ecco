import at.jku.isse.ecco.adapter.c.CWriter;
import at.jku.isse.ecco.adapter.c.data.FunctionArtifactData;
import at.jku.isse.ecco.adapter.c.data.LineArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CWriterIntegrationTest {

    @BeforeAll
    public static void testPreparation(){
        createTestFolder();
    }

    @AfterAll
    public static void testCleanUp(){
        deleteTestFolder();
    }

    @AfterEach
    public void singleTestCleanUp(){
        deleteTestFolder();
        createTestFolder();
    }

    public static void createTestFolder(){
        Path testFolderPath = getTestFolderPath();
        try {
            Files.createDirectories(testFolderPath);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static void deleteTestFolder(){
        Path testFolderPath = getTestFolderPath();
        try {
            File dir = testFolderPath.toFile();
            if (dir.exists()) FileUtils.deleteDirectory(dir);
        }
        catch (IOException e){
            throw new RuntimeException(e.getMessage());
        }
    }

    public static Path getTestFolderPath(){
        return getResourceFolderPath("TestFolder");
    }

    public static Path getResourceFolderPath(String relativePath){
        try {
            URI configURI = Objects.requireNonNull(CWriterIntegrationTest.class.getClassLoader().getResource("WriterTestFolder")).toURI();
            Path baseFolderPath = Paths.get(configURI);
            return baseFolderPath.resolve(relativePath);
        } catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

    public static Path getTestFolderSubpath(String relativePath){
        Path testFolderPath = getTestFolderPath();
        return testFolderPath.resolve(relativePath);
    }

    public static String[] fileToStringArray(Path filePath){
        List<String> lineList = new LinkedList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                lineList.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String[] lineArray = new String[lineList.size()];
        lineList.toArray(lineArray);
        return lineArray;
    }

    @Test
    public void writeSingleLine() {
        MemEntityFactory factory = new MemEntityFactory();

        // create ecco tree with some line nodes
        Path testFilePath = getTestFolderSubpath("testFile.c");
        Artifact.Op<PluginArtifactData> pluginArtifact = factory.createArtifact(new PluginArtifactData("testId", testFilePath));
        Node.Op pluginNode = factory.createNode(pluginArtifact);

        Artifact.Op<LineArtifactData> lineArtifact = factory.createArtifact(new LineArtifactData("single test line;"));
        Node.Op node = factory.createNode(lineArtifact);
        pluginNode.addChild(node);

        // write tree
        CWriter writer = new CWriter();
        Set<Node> nodeSet = new HashSet<>();
        nodeSet.add(pluginNode);
        writer.write(getTestFolderPath(), nodeSet);

        // check new file exists
        assertTrue(Files.exists(testFilePath));

        // check files in new file
        String[] writtenContent = fileToStringArray(testFilePath);
        assertEquals(1, writtenContent.length);
        assertEquals("single test line;", writtenContent[0]);
    }

    @Test
    public void writeMultipleLines(){
        MemEntityFactory factory = new MemEntityFactory();

        // create ecco tree with some line nodes
        Path testFilePath = getTestFolderSubpath("testFile.c");
        Artifact.Op<PluginArtifactData> pluginArtifact = factory.createArtifact(new PluginArtifactData("testId", testFilePath));
        Node.Op pluginNode = factory.createNode(pluginArtifact);

        Artifact.Op<LineArtifactData> lineArtifact = factory.createArtifact(new LineArtifactData("first test line;"));
        Node.Op node = factory.createNode(lineArtifact);
        pluginNode.addChild(node);
        lineArtifact = factory.createArtifact(new LineArtifactData("second test line;"));
        node = factory.createNode(lineArtifact);
        pluginNode.addChild(node);

        // write tree
        CWriter writer = new CWriter();
        Set<Node> nodeSet = new HashSet<>();
        nodeSet.add(pluginNode);
        writer.write(getTestFolderPath(), nodeSet);

        // check new file exists
        assertTrue(Files.exists(testFilePath));

        // check files in new file
        String[] writtenContent = fileToStringArray(testFilePath);
        assertEquals(2, writtenContent.length);
        assertEquals("first test line;", writtenContent[0]);
        assertEquals("second test line;", writtenContent[1]);
    }

    @Test
    public void writeFunction(){
        MemEntityFactory factory = new MemEntityFactory();

        Path testFilePath = getTestFolderSubpath("testFile.c");
        Artifact.Op<PluginArtifactData> pluginArtifact = factory.createArtifact(new PluginArtifactData("testId", testFilePath));
        Node.Op pluginNode = factory.createNode(pluginArtifact);

        Artifact.Op<FunctionArtifactData> functionArtifact = factory.createArtifact(new FunctionArtifactData("voidfeatureA()"));
        Node.Op functionNode = factory.createNode(functionArtifact);
        pluginNode.addChild(functionNode);

        Artifact.Op<LineArtifactData> lineArtifact = factory.createArtifact(new LineArtifactData("void featureA() {"));
        Node.Op node = factory.createNode(lineArtifact);
        functionNode.addChild(node);
        lineArtifact = factory.createArtifact(new LineArtifactData("    printf(\"Hello, this is Feature A!\\n\");"));
        node = factory.createNode(lineArtifact);
        functionNode.addChild(node);
        lineArtifact = factory.createArtifact(new LineArtifactData("}"));
        node = factory.createNode(lineArtifact);
        functionNode.addChild(node);

        // write tree
        CWriter writer = new CWriter();
        Set<Node> nodeSet = new HashSet<>();
        nodeSet.add(pluginNode);
        writer.write(getTestFolderPath(), nodeSet);

        // check new file exists
        assertTrue(Files.exists(testFilePath));

        // check files in new file
        String[] writtenContent = fileToStringArray(testFilePath);
        assertEquals(3, writtenContent.length);
        assertEquals("void featureA() {", writtenContent[0]);
        assertEquals("    printf(\"Hello, this is Feature A!\\n\");", writtenContent[1]);
        assertEquals("}", writtenContent[2]);
    }

    @Test
    public void writeFunctionsAndLines(){
        MemEntityFactory factory = new MemEntityFactory();

        Path testFilePath = getTestFolderSubpath("testFile.c");
        Artifact.Op<PluginArtifactData> pluginArtifact = factory.createArtifact(new PluginArtifactData("testId", testFilePath));
        Node.Op pluginNode = factory.createNode(pluginArtifact);

        Artifact.Op<LineArtifactData> lineArtifact = factory.createArtifact(new LineArtifactData("first simple test line;"));
        Node.Op node = factory.createNode(lineArtifact);
        pluginNode.addChild(node);

        Artifact.Op<FunctionArtifactData> functionArtifact = factory.createArtifact(new FunctionArtifactData("voidfeatureA()"));
        Node.Op functionNode = factory.createNode(functionArtifact);
        pluginNode.addChild(functionNode);

        lineArtifact = factory.createArtifact(new LineArtifactData("void featureA() {"));
        node = factory.createNode(lineArtifact);
        functionNode.addChild(node);
        lineArtifact = factory.createArtifact(new LineArtifactData("    printf(\"Hello, this is Feature A!\\n\");"));
        node = factory.createNode(lineArtifact);
        functionNode.addChild(node);
        lineArtifact = factory.createArtifact(new LineArtifactData("}"));
        node = factory.createNode(lineArtifact);
        functionNode.addChild(node);

        lineArtifact = factory.createArtifact(new LineArtifactData("second simple test line;"));
        node = factory.createNode(lineArtifact);
        pluginNode.addChild(node);

        // write tree
        CWriter writer = new CWriter();
        Set<Node> nodeSet = new HashSet<>();
        nodeSet.add(pluginNode);
        writer.write(getTestFolderPath(), nodeSet);

        // check new file exists
        assertTrue(Files.exists(testFilePath));

        // check files in new file
        String[] writtenContent = fileToStringArray(testFilePath);
        assertEquals(5, writtenContent.length);
        assertEquals("first simple test line;", writtenContent[0]);
        assertEquals("void featureA() {", writtenContent[1]);
        assertEquals("    printf(\"Hello, this is Feature A!\\n\");", writtenContent[2]);
        assertEquals("}", writtenContent[3]);
        assertEquals("second simple test line;", writtenContent[4]);
    }

    @Test
    public void writeMultipleFiles(){
        MemEntityFactory factory = new MemEntityFactory();

        Path testFilePath1 = getTestFolderSubpath("testFile1.c");
        Artifact.Op<PluginArtifactData> pluginArtifact = factory.createArtifact(new PluginArtifactData("testId1", testFilePath1));
        Node.Op pluginNode1 = factory.createNode(pluginArtifact);

        Artifact.Op<LineArtifactData> lineArtifact = factory.createArtifact(new LineArtifactData("single test line;"));
        Node.Op node = factory.createNode(lineArtifact);
        pluginNode1.addChild(node);

        Path testFilePath2 = getTestFolderSubpath("testFile2.c");
        pluginArtifact = factory.createArtifact(new PluginArtifactData("testId2", testFilePath2));
        Node.Op pluginNode2 = factory.createNode(pluginArtifact);

        lineArtifact = factory.createArtifact(new LineArtifactData("single test line;"));
        node = factory.createNode(lineArtifact);
        pluginNode2.addChild(node);

        // write tree
        CWriter writer = new CWriter();
        Set<Node> nodeSet = new HashSet<>();
        nodeSet.add(pluginNode1);
        nodeSet.add(pluginNode2);
        writer.write(getTestFolderPath(), nodeSet);

        // check new file exists
        assertTrue(Files.exists(testFilePath1));
        assertTrue(Files.exists(testFilePath2));

        // check files in new file
        String[] writtenContent = fileToStringArray(testFilePath1);
        assertEquals(1, writtenContent.length);
        assertEquals("single test line;", writtenContent[0]);

        writtenContent = fileToStringArray(testFilePath2);
        assertEquals(1, writtenContent.length);
        assertEquals("single test line;", writtenContent[0]);
    }

    @Test
    public void writeCreatesCorrectReturnValue(){
        MemEntityFactory factory = new MemEntityFactory();

        Path testFilePath1 = getTestFolderSubpath("testFile1.c");
        Artifact.Op<PluginArtifactData> pluginArtifact = factory.createArtifact(new PluginArtifactData("testId1", testFilePath1));
        Node.Op pluginNode1 = factory.createNode(pluginArtifact);

        Artifact.Op<LineArtifactData> lineArtifact = factory.createArtifact(new LineArtifactData("single test line;"));
        Node.Op node = factory.createNode(lineArtifact);
        pluginNode1.addChild(node);

        Path testFilePath2 = getTestFolderSubpath("testFile2.c");
        pluginArtifact = factory.createArtifact(new PluginArtifactData("testId2", testFilePath2));
        Node.Op pluginNode2 = factory.createNode(pluginArtifact);

        lineArtifact = factory.createArtifact(new LineArtifactData("single test line;"));
        node = factory.createNode(lineArtifact);
        pluginNode2.addChild(node);

        CWriter writer = new CWriter();
        Set<Node> nodeSet = new HashSet<>();
        nodeSet.add(pluginNode1);
        nodeSet.add(pluginNode2);
        Path[] pathResults = writer.write(getTestFolderPath(), nodeSet);

        assertEquals(getTestFolderSubpath("testFile2.c"), pathResults[0]);
        assertEquals(getTestFolderSubpath("testFile1.c"), pathResults[1]);
    }

}
