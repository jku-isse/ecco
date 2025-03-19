package at.jku.isse.ecco.adapter.challenge.test;

import at.jku.isse.ecco.adapter.challenge.JavaChallengeReader;
import at.jku.isse.ecco.adapter.challenge.data.*;
import at.jku.isse.ecco.adapter.challenge.test.utils.PatternUtils;
import at.jku.isse.ecco.adapter.challenge.test.utils.ResourceUtils;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ChallengeTraceReaderTest {

    @Test
    public void readerParsesTracesExampleCorrectly(){
        Path variantFolderPath = ResourceUtils.getResourceFolderPath("test_variant_feature_traces");
        JavaChallengeReader reader = new JavaChallengeReader(new MemEntityFactory());
        Collection<Path> relativeFiles = PatternUtils.getRelativeDirContent(reader, variantFolderPath);
        Path[] relativeFileAr = relativeFiles.toArray(new Path[0]);

        Set<Node.Op> nodes = reader.read(variantFolderPath, relativeFileAr);
        assertEquals(2, nodes.size());

        // check parsing result of TestClassA.java
        Path fileAPath = Paths.get("TestClassA.java");
        Node.Op fileANode = getNodeWithArtifactDataFromCollection(nodes, new PluginArtifactData("placeholderid", fileAPath));
        assertNull(fileANode.getLocation());
        assertFalse(fileANode.getFeatureTrace().containsProactiveCondition());

        List<Node.Op> classANodes = (List<Node.Op>) fileANode.getChildren();
        assertEquals(1, classANodes.size());
        Node.Op classANode = getNodeWithArtifactDataFromCollection(classANodes, new ClassArtifactData("org.test.TestClassA"));
        checkNodeTrace(classANode, "FEATUREX");
        checkNodeLocation(classANode, 6, 35);
        this.checkTestClassA(classANode);

        // check parsing result of TestClassB.java
        Path fileBPath = Paths.get("TestClassB.java");
        Node.Op fileBNode = getNodeWithArtifactDataFromCollection(nodes, new PluginArtifactData("placeholderid", fileBPath));
        assertNull(fileANode.getLocation());
        assertFalse(fileANode.getFeatureTrace().containsProactiveCondition());
        List<Node.Op> classBNodes = (List<Node.Op>) fileBNode.getChildren();
        assertEquals(1, classBNodes.size());
        Node.Op classBNode = getNodeWithArtifactDataFromCollection(classBNodes, new ClassArtifactData("org.test.TestClassB"));
        assertFalse(fileANode.getFeatureTrace().containsProactiveCondition());
        checkNodeLocation(classBNode, 7, 33);
        this.checkTestClassB(classBNode);
    }

    private void checkTestClassA(Node.Op testClassANode){
        List<Node.Op> groupNodes = (List<Node.Op>) testClassANode.getChildren();
        assertEquals(4, groupNodes.size());

        Node.Op importGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("IMPORTS"));
        assertNull(importGroupNode.getLocation());
        assertFalse(importGroupNode.getFeatureTrace().containsProactiveCondition());
        this.checkTestClassAImports(importGroupNode);

        Node.Op methodsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("METHODS"));
        assertNull(methodsGroupNode.getLocation());
        assertFalse(methodsGroupNode.getFeatureTrace().containsProactiveCondition());
        this.checkTestClassAMethods(methodsGroupNode);

        Node.Op fieldsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("FIELDS"));
        assertNull(fieldsGroupNode.getLocation());
        assertFalse(fieldsGroupNode.getFeatureTrace().containsProactiveCondition());
        this.checkTestClassAFields(fieldsGroupNode);

        Node.Op enumsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("ENUMS"));
        assertNull(enumsGroupNode.getLocation());
        assertFalse(enumsGroupNode.getFeatureTrace().containsProactiveCondition());
        this.checkTestClassAEnums(enumsGroupNode);
    }

    private void checkTestClassAImports(Node.Op importGroupNode){
        List<Node.Op> importNodes = (List<Node.Op>) importGroupNode.getChildren();
        assertEquals(2, importNodes.size());

        Node.Op importNode;
        importNode = getNodeWithArtifactDataFromCollection(importNodes, new ImportArtifactData("import org.test.extern.x"));
        checkNodeTrace(importNode, "FEATUREX");
        checkNodeLocation(importNode, 3, 3);

        importNode = getNodeWithArtifactDataFromCollection(importNodes, new ImportArtifactData("import org.test.extern.y"));
        checkNodeTrace(importNode, "FEATUREX");
        checkNodeLocation(importNode, 4, 4);
    }

    private void checkTestClassAMethods(Node.Op methodsGroupNode){
        List<Node.Op> methodNodes = (List<Node.Op>) methodsGroupNode.getChildren();
        assertEquals(4, methodNodes.size());
        Node.Op lineNode;

        Node.Op constructor = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("TestClassA()"));
        checkNodeTrace(constructor, "FEATUREX");
        checkNodeLocation(constructor, 17, 20);
        List<Node.Op> constructorLineNodes = (List<Node.Op>) constructor.getChildren();
        assertEquals(2, constructorLineNodes.size());
        lineNode = getNodeWithArtifactDataFromCollection(constructorLineNodes, new LineArtifactData("        // comment in constructor"));
        checkNodeTrace(lineNode, "FEATUREX");
        checkNodeLocation(lineNode, 18, 18);
        lineNode = getNodeWithArtifactDataFromCollection(constructorLineNodes, new LineArtifactData("        System.out.println(\"constructor\");"));
        checkNodeTrace(lineNode, "FEATUREX");
        checkNodeLocation(lineNode, 19, 19);

        Node.Op stringMethod = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("stringMethod(int)"));
        checkNodeTrace(stringMethod, "FEATUREX");
        checkNodeLocation(stringMethod, 22, 26);
        List<Node.Op> stringMethodLineNodes = (List<Node.Op>) stringMethod.getChildren();
        assertEquals(3, stringMethodLineNodes.size());
        lineNode = getNodeWithArtifactDataFromCollection(stringMethodLineNodes, new LineArtifactData("        // comment in method"));
        checkNodeTrace(lineNode, "FEATUREX & FEATUREY");
        checkNodeLocation(lineNode, 23, 23);
        lineNode = getNodeWithArtifactDataFromCollection(stringMethodLineNodes, new LineArtifactData("        String stringVariable = \"String\";"));
        checkNodeTrace(lineNode, "FEATUREX & FEATUREY");
        checkNodeLocation(lineNode, 24, 24);
        lineNode = getNodeWithArtifactDataFromCollection(stringMethodLineNodes, new LineArtifactData("        return stringVariable;"));
        checkNodeTrace(lineNode, "FEATUREX");
        checkNodeLocation(lineNode, 25, 25);

        Node.Op privateMethod = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("privateMethod()"));
        checkNodeTrace(privateMethod, "FEATUREX");
        checkNodeLocation(privateMethod, 28, 30);
        List<Node.Op> privateMethodLineNodes = (List<Node.Op>) privateMethod.getChildren();
        assertEquals(1, privateMethodLineNodes.size());
        lineNode = getNodeWithArtifactDataFromCollection(privateMethodLineNodes, new LineArtifactData("        System.out.println(\"private method\");"));
        checkNodeTrace(lineNode, "FEATUREX");
        checkNodeLocation(lineNode, 29, 29);

        Node.Op staticMethod = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("staticMethod()"));
        checkNodeTrace(staticMethod, "FEATUREX");
        checkNodeLocation(staticMethod, 32, 34);
        List<Node.Op> staticMethodLineNodes = (List<Node.Op>) staticMethod.getChildren();
        assertEquals(1, staticMethodLineNodes.size());
        lineNode = getNodeWithArtifactDataFromCollection(staticMethodLineNodes, new LineArtifactData("        return 1;"));
        checkNodeTrace(lineNode, "FEATUREX");
        checkNodeLocation(lineNode, 33, 33);
    }

    private void checkTestClassAFields(Node.Op fieldGroupNode){
        List<Node.Op> fieldNodes = (List<Node.Op>) fieldGroupNode.getChildren();
        assertEquals(2, fieldNodes.size());
        Node.Op lineNode;

        lineNode = getNodeWithArtifactDataFromCollection(fieldNodes, new LineArtifactData("    private int intField;"));
        checkNodeTrace(lineNode, "FEATUREX");
        checkNodeLocation(lineNode, 14, 14);
        lineNode = getNodeWithArtifactDataFromCollection(fieldNodes, new LineArtifactData("    private String stringField;"));
        checkNodeTrace(lineNode, "FEATUREX");
        checkNodeLocation(lineNode, 15, 15);
    }

    private void checkTestClassAEnums(Node.Op enumsGroupNode){
        List<Node.Op> enumsNodes = (List<Node.Op>) enumsGroupNode.getChildren();
        assertEquals(4, enumsNodes.size());
        Node.Op lineNode;

        lineNode = getNodeWithArtifactDataFromCollection(enumsNodes, new LineArtifactData("    enum testEnum {"));
        checkNodeTrace(lineNode, "FEATUREX");
        checkNodeLocation(lineNode, 8, 8);
        lineNode = getNodeWithArtifactDataFromCollection(enumsNodes, new LineArtifactData("        VALUEX,"));
        checkNodeTrace(lineNode, "FEATUREX");
        checkNodeLocation(lineNode, 9, 9);
        lineNode = getNodeWithArtifactDataFromCollection(enumsNodes, new LineArtifactData("        VALUEY,"));
        checkNodeTrace(lineNode, "FEATUREX");
        checkNodeLocation(lineNode, 10, 10);
        lineNode = getNodeWithArtifactDataFromCollection(enumsNodes, new LineArtifactData("        VALUEZ"));
        checkNodeTrace(lineNode, "FEATUREX");
        checkNodeLocation(lineNode, 11, 11);
    }

    private void checkTestClassB(Node.Op testClassBNode){
        List<Node.Op> groupNodes = (List<Node.Op>) testClassBNode.getChildren();
        assertEquals(5, groupNodes.size());

        Node.Op importGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("IMPORTS"));
        assertNull(importGroupNode.getLocation());
        assertFalse(importGroupNode.getFeatureTrace().containsProactiveCondition());
        this.checkTestClassBImports(importGroupNode);

        Node.Op methodsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("METHODS"));
        assertNull(methodsGroupNode.getLocation());
        assertFalse(methodsGroupNode.getFeatureTrace().containsProactiveCondition());
        this.checkTestClassBMethods(methodsGroupNode);

        Node.Op fieldsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("FIELDS"));
        assertNull(fieldsGroupNode.getLocation());
        assertFalse(fieldsGroupNode.getFeatureTrace().containsProactiveCondition());
        this.checkTestClassBFields(fieldsGroupNode);

        Node.Op enumsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("ENUMS"));
        assertNull(enumsGroupNode.getLocation());
        assertFalse(enumsGroupNode.getFeatureTrace().containsProactiveCondition());
        this.checkTestClassBEnums(enumsGroupNode);

        Node.Op innerClassNode = getNodeWithArtifactDataFromCollection(groupNodes, new ClassArtifactData("org.test.TestClassB.InnerClass"));
        checkNodeTrace(innerClassNode, "FEATUREZ");
        checkNodeLocation(innerClassNode, 21, 32);
        this.checkTestClassBInnerClass(innerClassNode);
    }

    private void checkTestClassBImports(Node.Op importGroupNode){
        List<Node.Op> importNodes = (List<Node.Op>) importGroupNode.getChildren();
        assertEquals(2, importNodes.size());

        Node.Op importNode;
        importNode = getNodeWithArtifactDataFromCollection(importNodes, new ImportArtifactData("import org.test.extern.x"));
        checkNodeTrace(importNode, "FEATUREZ");
        checkNodeLocation(importNode, 3, 3);
        importNode = getNodeWithArtifactDataFromCollection(importNodes, new ImportArtifactData("import org.test.extern.y"));
        checkNodeTrace(importNode, "FEATUREZ");
        checkNodeLocation(importNode, 5, 5);
    }

    private void checkTestClassBMethods(Node.Op methodsGroupNode){
        List<Node.Op> methodNodes = (List<Node.Op>) methodsGroupNode.getChildren();
        assertEquals(2, methodNodes.size());
        Node.Op lineNode;

        Node.Op constructor = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("TestClassB()"));
        checkNodeTrace(constructor, "FEATUREZ");
        checkNodeLocation(constructor, 12, 14);
        List<Node.Op> constructorLineNodes = (List<Node.Op>) constructor.getChildren();
        assertEquals(1, constructorLineNodes.size());
        lineNode = getNodeWithArtifactDataFromCollection(constructorLineNodes, new LineArtifactData("        System.out.println(\"constructor\");"));
        checkNodeTrace(lineNode, "FEATUREZ");
        checkNodeLocation(lineNode, 13, 13);

        Node.Op stringMethod = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("stringMethod(int)"));
        checkNodeTrace(stringMethod, "FEATUREZ");
        checkNodeLocation(stringMethod, 16, 19);
        List<Node.Op> stringMethodLineNodes = (List<Node.Op>) stringMethod.getChildren();
        assertEquals(2, stringMethodLineNodes.size());
        lineNode = getNodeWithArtifactDataFromCollection(stringMethodLineNodes, new LineArtifactData("        String stringVariable = \"String\";"));
        checkNodeTrace(lineNode, "FEATUREZ");
        checkNodeLocation(lineNode, 17, 17);
        lineNode = getNodeWithArtifactDataFromCollection(stringMethodLineNodes, new LineArtifactData("        return stringVariable;"));
        checkNodeTrace(lineNode, "FEATUREZ");
        checkNodeLocation(lineNode, 18, 18);
    }

    private void checkTestClassBFields(Node.Op fieldGroupNode){
        List<Node.Op> fieldNodes = (List<Node.Op>) fieldGroupNode.getChildren();
        assertEquals(2, fieldNodes.size());
        Node.Op lineNode;

        lineNode = getNodeWithArtifactDataFromCollection(fieldNodes, new LineArtifactData("    private int intField;"));
        checkNodeTrace(lineNode, "FEATUREZ");
        checkNodeLocation(lineNode, 9, 9);
        lineNode = getNodeWithArtifactDataFromCollection(fieldNodes, new LineArtifactData("    private String stringField;"));
        checkNodeTrace(lineNode, "FEATUREZ");
        checkNodeLocation(lineNode, 10, 10);
    }

    private void checkTestClassBEnums(Node.Op enumsGroupNode){
        List<Node.Op> enumsNodes = (List<Node.Op>) enumsGroupNode.getChildren();
        assertEquals(0, enumsNodes.size());
    }

    private void checkTestClassBInnerClass(Node.Op innerClassNode){
        List<Node.Op> groupNodes = (List<Node.Op>) innerClassNode.getChildren();
        assertEquals(3, groupNodes.size());

        Node.Op methodsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("METHODS"));
        assertNull(methodsGroupNode.getLocation());
        assertFalse(methodsGroupNode.getFeatureTrace().containsProactiveCondition());
        this.checkInnerClassMethods(methodsGroupNode);

        Node.Op fieldsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("FIELDS"));
        assertNull(fieldsGroupNode.getLocation());
        assertFalse(fieldsGroupNode.getFeatureTrace().containsProactiveCondition());
        this.checkInnerClassFields(fieldsGroupNode);

        Node.Op enumsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("ENUMS"));
        assertNull(enumsGroupNode.getLocation());
        assertFalse(enumsGroupNode.getFeatureTrace().containsProactiveCondition());
        this.checkInnerClassEnums(enumsGroupNode);
    }

    private void checkInnerClassMethods(Node.Op methodsGroupNode){
        List<Node.Op> methodNodes = (List<Node.Op>) methodsGroupNode.getChildren();
        assertEquals(2, methodNodes.size());
        Node.Op lineNode;

        Node.Op constructor = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("InnerClass()"));
        checkNodeTrace(constructor, "FEATUREZ");
        checkNodeLocation(constructor, 25, 27);
        List<Node.Op> constructorLineNodes = (List<Node.Op>) constructor.getChildren();
        assertEquals(1, constructorLineNodes.size());
        lineNode = getNodeWithArtifactDataFromCollection(constructorLineNodes, new LineArtifactData("            System.out.println(\"constructor inner class\");"));
        checkNodeTrace(lineNode, "FEATUREZ");
        checkNodeLocation(lineNode, 26, 26);

        Node.Op innerMethod = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("innerClassMethod(boolean)"));
        checkNodeTrace(innerMethod, "FEATUREZ");
        checkNodeLocation(innerMethod, 29, 31);
        List<Node.Op> innerMethodLineNodes = (List<Node.Op>) innerMethod.getChildren();
        assertEquals(1, innerMethodLineNodes.size());
        lineNode = getNodeWithArtifactDataFromCollection(innerMethodLineNodes, new LineArtifactData("            System.out.println(\"inner class method\");"));
        checkNodeTrace(lineNode, "FEATUREZ");
        checkNodeLocation(lineNode, 30, 30);
    }

    private void checkInnerClassFields(Node.Op fieldGroupNode){
        List<Node.Op> fieldNodes = (List<Node.Op>) fieldGroupNode.getChildren();
        assertEquals(1, fieldNodes.size());

        Node.Op lineNode = getNodeWithArtifactDataFromCollection(fieldNodes, new LineArtifactData("        private boolean booleanField;"));
        checkNodeTrace(lineNode, "FEATUREZ");
        checkNodeLocation(lineNode, 23, 23);
    }

    private void checkInnerClassEnums(Node.Op enumsGroupNode){
        List<Node.Op> enumsNodes = (List<Node.Op>) enumsGroupNode.getChildren();
        assertEquals(0, enumsNodes.size());
    }

    private static Node.Op getNodeWithArtifactDataFromCollection(Collection<Node.Op> nodes, ArtifactData artifactData){
        Optional<Node.Op> optionalNode = nodes.stream().filter(node -> node.getArtifact().getData().equals(artifactData)).findFirst();
        if (optionalNode.isPresent()){
            return optionalNode.get();
        } else {
            throw new RuntimeException("Collection of nodes does not contain given artifact data: " + artifactData);
        }
    }

    private static void checkNodeTrace(Node.Op node, String featureTrace){
        String parsedTrace = node.getFeatureTrace().getProactiveConditionString();
        assertEquals(parsedTrace, featureTrace);
    }

    private static void checkNodeLocation(Node.Op node, int startLine, int endLine){
        Location nodeLocation = node.getLocation();
        assertEquals(nodeLocation.getStartLine(), startLine);
        assertEquals(nodeLocation.getEndLine(), endLine);
    }

}
