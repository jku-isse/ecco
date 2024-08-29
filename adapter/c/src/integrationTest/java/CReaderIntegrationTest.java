import at.jku.isse.ecco.adapter.c.CReader;
import at.jku.isse.ecco.adapter.c.data.AbstractArtifactData;
import at.jku.isse.ecco.adapter.c.data.FunctionArtifactData;
import at.jku.isse.ecco.adapter.c.data.LineArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class CReaderIntegrationTest {

    @Test
    public void readFileTest() throws URISyntaxException {
        String relativeResourceFolderPath = "C_SPL/simple_file";
        URI resourceFolderUri = Objects.requireNonNull(getClass().getClassLoader().getResource(relativeResourceFolderPath)).toURI();
        String resourceFolderPathString = Paths.get(resourceFolderUri).toString();
        Path resourceFolderPath = Paths.get(resourceFolderPathString);

        Set<Node.Op> nodes = readFolder(resourceFolderPath);

        assertEquals(1, nodes.size());
        Node.Op resultPluginNode = nodes.iterator().next();
        testSimpleFile(resultPluginNode);
    }

    @Test
    public void testGrammarBugHandling() throws URISyntaxException {
        // a buggy function will not be added as function node but as multiple line nodes

        String relativeResourceFolderPath = "C_SPL/buggy_file";
        URI resourceFolderUri = Objects.requireNonNull(getClass().getClassLoader().getResource(relativeResourceFolderPath)).toURI();
        String resourceFolderPathString = Paths.get(resourceFolderUri).toString();
        Path resourceFolderPath = Paths.get(resourceFolderPathString);

        Set<Node.Op> nodes = readFolder(resourceFolderPath);

        assertEquals(1, nodes.size());
        Node.Op resultPluginNode = nodes.iterator().next();
        List<Node.Op> pluginNodeChildren = (List<Node.Op>) resultPluginNode.getChildren();
        assertEquals(1, pluginNodeChildren.size());

        Node.Op orderingNode = pluginNodeChildren.iterator().next();
        Artifact<?> orderingArtifact = orderingNode.getArtifact();
        assertTrue(orderingArtifact.isOrdered());
        ArtifactData orderingArtifactData = orderingArtifact.getData();
        assertTrue(orderingArtifactData instanceof AbstractArtifactData);
        assertEquals("Ordering Artifact", ((AbstractArtifactData) orderingArtifactData).getId());

        assertEquals(11, orderingNode.getNumberOfChildren());
        List<Node.Op> firstLevelNodes = (List<Node.Op>) orderingNode.getChildren();
        checkLineNode(firstLevelNodes.get(0), "#include <stdio.h>");
        checkLineNode(firstLevelNodes.get(1), "int main({");
        checkLineNode(firstLevelNodes.get(2), "    printf(\"Base Product\\n\");");
        checkLineNode(firstLevelNodes.get(3), "    // Feature A");
        checkUserCondition(firstLevelNodes.get(3), "FEATUREA");
        checkLineNode(firstLevelNodes.get(4), "    featureA();");
        checkUserCondition(firstLevelNodes.get(4), "FEATUREA");
        checkLineNode(firstLevelNodes.get(5), "    // Feature A || B");
        checkUserCondition(firstLevelNodes.get(5), "(FEATUREA | FEATUREB)");
        checkLineNode(firstLevelNodes.get(6), "    featureAOrB();");
        checkUserCondition(firstLevelNodes.get(6), "(FEATUREA | FEATUREB)");
        checkLineNode(firstLevelNodes.get(7), "    return 0;");
        checkLineNode(firstLevelNodes.get(8), "}");

        Node.Op functionNode = firstLevelNodes.get(9);
        checkFunctionNode(functionNode, "voidfeatureA()");
        assertEquals(3, functionNode.getNumberOfChildren());
        List<Node.Op> functionLines = (List<Node.Op>) functionNode.getChildren();
        checkLineNode(functionLines.get(0), "void featureA() {");
        checkUserCondition(functionLines.get(0), "FEATUREA");
        checkLineNode(functionLines.get(1), "    printf(\"Hello, this is Feature A!\\n\");");
        checkUserCondition(functionLines.get(1), "FEATUREA");
        checkLineNode(functionLines.get(2), "}");
        checkUserCondition(functionLines.get(2), "FEATUREA");

        functionNode = firstLevelNodes.get(10);
        checkFunctionNode(functionNode, "voidfeatureAOrB()");
        assertEquals(3, functionNode.getNumberOfChildren());
        functionLines = (List<Node.Op>) functionNode.getChildren();
        checkLineNode(functionLines.get(0), "void featureAOrB() {");
        checkUserCondition(functionLines.get(0), "(FEATUREA | FEATUREB)");
        checkLineNode(functionLines.get(1), "    printf(\"Hello, this is Feature A || B!\\n\");");
        checkUserCondition(functionLines.get(1), "(FEATUREA | FEATUREB)");
        checkLineNode(functionLines.get(2), "}");
        checkUserCondition(functionLines.get(2), "(FEATUREA | FEATUREB)");
    }

    @Test
    public void readMultipleFiles() throws URISyntaxException {
        String relativeResourceFolderPath = "C_SPL/multiple_files";
        URI resourceFolderUri = Objects.requireNonNull(getClass().getClassLoader().getResource(relativeResourceFolderPath)).toURI();
        String resourceFolderPathString = Paths.get(resourceFolderUri).toString();
        Path resourceFolderPath = Paths.get(resourceFolderPathString);

        Set<Node.Op> nodes = readFolder(resourceFolderPath);
        nodes.forEach(this::testSimpleFile);
    }

    private void testSimpleFile(Node.Op pluginNode){
        List<Node.Op> pluginNodeChildren = (List<Node.Op>) pluginNode.getChildren();
        assertEquals(1, pluginNodeChildren.size());

        Node.Op orderingNode = pluginNodeChildren.iterator().next();
        Artifact<?> orderingArtifact = orderingNode.getArtifact();
        assertTrue(orderingArtifact.isOrdered());
        ArtifactData orderingArtifactData = orderingArtifact.getData();
        assertTrue(orderingArtifactData instanceof AbstractArtifactData);
        assertEquals("Ordering Artifact", ((AbstractArtifactData) orderingArtifactData).getId());

        assertEquals(4, orderingNode.getNumberOfChildren());
        List<Node.Op> firstLevelNodes = (List<Node.Op>) orderingNode.getChildren();
        checkLineNode(firstLevelNodes.get(0), "#include <stdio.h>");

        Node.Op functionNode = firstLevelNodes.get(1);
        checkFunctionNode(functionNode, "intmain()");
        assertEquals(8, functionNode.getNumberOfChildren());
        List<Node.Op> functionLines = (List<Node.Op>) functionNode.getChildren();
        checkLineNode(functionLines.get(0), "int main() {");
        checkLineNode(functionLines.get(1), "    printf(\"Base Product\\n\");");
        checkLineNode(functionLines.get(2), "    // Feature A");
        checkUserCondition(functionLines.get(2), "FEATUREA");
        checkLineNode(functionLines.get(3), "    featureA();");
        checkUserCondition(functionLines.get(3), "FEATUREA");
        checkLineNode(functionLines.get(4), "    // Feature A || B");
        checkUserCondition(functionLines.get(4), "(FEATUREA | FEATUREB)");
        checkLineNode(functionLines.get(5), "    featureAOrB();");
        checkUserCondition(functionLines.get(5), "(FEATUREA | FEATUREB)");
        checkLineNode(functionLines.get(6), "    return 0;");
        checkLineNode(functionLines.get(7), "}");

        functionNode = firstLevelNodes.get(2);
        checkFunctionNode(functionNode, "voidfeatureA()");
        assertEquals(3, functionNode.getNumberOfChildren());
        functionLines = (List<Node.Op>) functionNode.getChildren();
        checkLineNode(functionLines.get(0), "void featureA() {");
        checkUserCondition(functionLines.get(0), "FEATUREA");
        checkLineNode(functionLines.get(1), "    printf(\"Hello, this is Feature A!\\n\");");
        checkUserCondition(functionLines.get(1), "FEATUREA");
        checkLineNode(functionLines.get(2), "}");
        checkUserCondition(functionLines.get(2), "FEATUREA");

        functionNode = firstLevelNodes.get(3);
        checkFunctionNode(functionNode, "voidfeatureAOrB()");
        assertEquals(3, functionNode.getNumberOfChildren());
        functionLines = (List<Node.Op>) functionNode.getChildren();
        checkLineNode(functionLines.get(0), "void featureAOrB() {");
        checkUserCondition(functionLines.get(0), "(FEATUREA | FEATUREB)");
        checkLineNode(functionLines.get(1), "    printf(\"Hello, this is Feature A || B!\\n\");");
        checkUserCondition(functionLines.get(1), "(FEATUREA | FEATUREB)");
        checkLineNode(functionLines.get(2), "}");
        checkUserCondition(functionLines.get(2), "(FEATUREA | FEATUREB)");
    }

    private Set<Node.Op> readFolder(Path folderPath){
        CReader reader = new CReader(new MemEntityFactory());
        Collection<Path> relativeFiles = this.getRelativeDirContent(reader, folderPath);
        Path[] relativeFileAr = relativeFiles.toArray(new Path[0]);
        return reader.read(folderPath, relativeFileAr);
    }

    private Collection<Path> getRelativeDirContent(CReader reader, Path dir){
        Map<Integer, String[]> prioritizedPatterns = reader.getPrioritizedPatterns();
        String[] patterns = prioritizedPatterns.values().iterator().next();
        Collection<PathMatcher> pathMatcher = Arrays.stream(patterns)
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .toList();

        Set<Path> fileSet = new HashSet<>();
        try (Stream<Path> pathStream = Files.walk(dir)) {
            pathStream.forEach( path -> {
                Boolean applicableFile = pathMatcher.stream().map(pm -> pm.matches(path)).reduce(Boolean::logicalOr).get();
                if (applicableFile) {
                    fileSet.add(path);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return fileSet.stream().map(dir::relativize).collect(Collectors.toList());
    }

    private void checkLineNode(Node.Op node, String line){
        assertNotNull(node);
        Artifact<?> artifact = node.getArtifact();
        ArtifactData artifactData = artifact.getData();
        assertTrue(artifactData instanceof LineArtifactData);
        assertEquals(line, ((LineArtifactData) artifactData).getLine());
    }

    private void checkUserCondition(Node.Op node, String conditionString){
        FeatureTrace featureTrace = node.getFeatureTrace();
        assertEquals(conditionString, featureTrace.getUserConditionString());
    }

    private void checkFunctionNode(Node.Op node, String signature){
        assertNotNull(node);
        Artifact<?> artifact = node.getArtifact();
        ArtifactData artifactData = artifact.getData();
        assertTrue(artifactData instanceof FunctionArtifactData);
        assertEquals(signature, ((FunctionArtifactData) artifactData).getSignature());
    }
}
