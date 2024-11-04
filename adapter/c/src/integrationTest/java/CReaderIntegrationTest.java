import at.jku.isse.ecco.adapter.c.CReader;
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
    public void readHeaderFileTest() throws URISyntaxException {
        String relativeResourceFolderPath = "C_SPL/header_file";
        URI resourceFolderUri = Objects.requireNonNull(getClass().getClassLoader().getResource(relativeResourceFolderPath)).toURI();
        String resourceFolderPathString = Paths.get(resourceFolderUri).toString();
        Path resourceFolderPath = Paths.get(resourceFolderPathString);

        Set<Node.Op> nodes = readFolder(resourceFolderPath);

        assertEquals(1, nodes.size());
        Node.Op resultPluginNode = nodes.iterator().next();
        testHeaderFile(resultPluginNode);
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

        assertEquals(11, resultPluginNode.getNumberOfChildren());
        checkLineNode(pluginNodeChildren.get(0), "#include <stdio.h>");
        checkLineNode(pluginNodeChildren.get(1), "int main({");
        checkLineNode(pluginNodeChildren.get(2), "    printf(\"Base Product\\n\");");
        checkLineNode(pluginNodeChildren.get(3), "    // Feature A");
        checkUserCondition(pluginNodeChildren.get(3), "FEATUREA");
        checkLineNode(pluginNodeChildren.get(4), "    featureA();");
        checkUserCondition(pluginNodeChildren.get(4), "FEATUREA");
        checkLineNode(pluginNodeChildren.get(5), "    // Feature A || B");
        checkUserCondition(pluginNodeChildren.get(5), "(FEATUREA | FEATUREB)");
        checkLineNode(pluginNodeChildren.get(6), "    featureAOrB();");
        checkUserCondition(pluginNodeChildren.get(6), "(FEATUREA | FEATUREB)");
        checkLineNode(pluginNodeChildren.get(7), "    return 0;");
        checkLineNode(pluginNodeChildren.get(8), "}");

        Node.Op functionNode = pluginNodeChildren.get(9);
        checkFunctionNode(functionNode, "voidfeatureA()");
        assertEquals(3, functionNode.getNumberOfChildren());
        List<Node.Op> functionLines = (List<Node.Op>) functionNode.getChildren();
        checkLineNode(functionLines.get(0), "void featureA() {");
        checkUserCondition(functionLines.get(0), "FEATUREA");
        checkLineNode(functionLines.get(1), "    printf(\"Hello, this is Feature A!\\n\");");
        checkUserCondition(functionLines.get(1), "FEATUREA");
        checkLineNode(functionLines.get(2), "}");
        checkUserCondition(functionLines.get(2), "FEATUREA");

        functionNode = pluginNodeChildren.get(10);
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

        assertEquals(4, pluginNodeChildren.size());
        checkLineNode(pluginNodeChildren.get(0), "#include <stdio.h>");

        Node.Op functionNode = pluginNodeChildren.get(1);
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

        functionNode = pluginNodeChildren.get(2);
        checkFunctionNode(functionNode, "voidfeatureA()");
        assertEquals(3, functionNode.getNumberOfChildren());
        functionLines = (List<Node.Op>) functionNode.getChildren();
        checkLineNode(functionLines.get(0), "void featureA() {");
        checkUserCondition(functionLines.get(0), "FEATUREA");
        checkLineNode(functionLines.get(1), "    printf(\"Hello, this is Feature A!\\n\");");
        checkUserCondition(functionLines.get(1), "FEATUREA");
        checkLineNode(functionLines.get(2), "}");
        checkUserCondition(functionLines.get(2), "FEATUREA");

        functionNode = pluginNodeChildren.get(3);
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


    private void testHeaderFile(Node.Op pluginNode){
        List<Node.Op> pluginNodeChildren = (List<Node.Op>) pluginNode.getChildren();

        assertEquals(13, pluginNodeChildren.size());
        checkLineNode(pluginNodeChildren.get(0), "#define WPU_PER_DCM (1200.0 / 2.54)");
        checkLineNode(pluginNodeChildren.get(1), "typedef struct");
        checkLineNode(pluginNodeChildren.get(2), "{");
        checkLineNode(pluginNodeChildren.get(3), "  guchar  fid[4];");
        checkLineNode(pluginNodeChildren.get(4), "  guint32 DataOffset;");
        checkLineNode(pluginNodeChildren.get(5), "  guint8  ProductType;");
        checkLineNode(pluginNodeChildren.get(6), "  guint8  FileType;");
        checkLineNode(pluginNodeChildren.get(7), "  guint8  MajorVersion;");
        checkLineNode(pluginNodeChildren.get(8), "  guint8  MinorVersion;");
        checkLineNode(pluginNodeChildren.get(9), "  guint16 EncryptionKey;");
        checkLineNode(pluginNodeChildren.get(10), "  guint16 Reserved;");
        checkLineNode(pluginNodeChildren.get(11), "}");
        checkLineNode(pluginNodeChildren.get(12), "WPGFileHead;");
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
