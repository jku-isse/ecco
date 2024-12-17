package at.jku.isse.ecco.adapter.challenge.test;

import at.jku.isse.ecco.adapter.challenge.JavaChallengeReader;
import at.jku.isse.ecco.adapter.challenge.data.*;
import at.jku.isse.ecco.adapter.challenge.test.utils.PatternUtils;
import at.jku.isse.ecco.adapter.challenge.test.utils.ResourceUtils;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ChallengeReaderTest {

    @Test
    public void readerParsesExampleCorrectly(){
        Path variantFolderPath = ResourceUtils.getResourceFolderPath("test_variant");
        JavaChallengeReader reader = new JavaChallengeReader(new MemEntityFactory());
        Collection<Path> relativeFiles = PatternUtils.getRelativeDirContent(reader, variantFolderPath);
        Path[] relativeFileAr = relativeFiles.toArray(new Path[0]);

        Set<Node.Op> nodes = reader.read(variantFolderPath, relativeFileAr);
        assertEquals(2, nodes.size());

        // check parsing result of TestClassA.java
        Path fileAPath = Paths.get("TestClassA.java");
        Node.Op fileANode = getNodeWithArtifactDataFromCollection(nodes, new PluginArtifactData("placeholderid", fileAPath));
        List<Node.Op> classANodes = (List<Node.Op>) fileANode.getChildren();
        assertEquals(1, classANodes.size());
        Node.Op classANode = getNodeWithArtifactDataFromCollection(classANodes, new ClassArtifactData("org.test.TestClassA"));
        this.checkTestClassA(classANode);

        // check parsing result of TestClassB.java
        Path fileBPath = Paths.get("TestClassB.java");
        Node.Op fileBNode = getNodeWithArtifactDataFromCollection(nodes, new PluginArtifactData("placeholderid", fileBPath));
        List<Node.Op> classBNodes = (List<Node.Op>) fileBNode.getChildren();
        assertEquals(1, classBNodes.size());
        Node.Op classBNode = getNodeWithArtifactDataFromCollection(classBNodes, new ClassArtifactData("org.test.TestClassB"));
        this.checkTestClassB(classBNode);
    }

    private void checkTestClassA(Node.Op testClassANode){
        List<Node.Op> groupNodes = (List<Node.Op>) testClassANode.getChildren();
        assertEquals(4, groupNodes.size());

        Node.Op importGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("IMPORTS"));
        this.checkTestClassAImports(importGroupNode);
        Node.Op methodsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("METHODS"));
        this.checkTestClassAMethods(methodsGroupNode);
        Node.Op fieldsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("FIELDS"));
        this.checkTestClassAFields(fieldsGroupNode);
        Node.Op enumsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("ENUMS"));
        this.checkTestClassAEnums(enumsGroupNode);
    }

    private void checkTestClassAImports(Node.Op importGroupNode){
        List<Node.Op> importNodes = (List<Node.Op>) importGroupNode.getChildren();
        assertEquals(2, importNodes.size());

        getNodeWithArtifactDataFromCollection(importNodes, new ImportArtifactData("import org.test.extern.x"));
        getNodeWithArtifactDataFromCollection(importNodes, new ImportArtifactData("import org.test.extern.y"));
    }

    private void checkTestClassAMethods(Node.Op methodsGroupNode){
        List<Node.Op> methodNodes = (List<Node.Op>) methodsGroupNode.getChildren();
        assertEquals(4, methodNodes.size());

        Node.Op constructor = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("TestClassA()"));
        List<Node.Op> constructorLineNodes = (List<Node.Op>) constructor.getChildren();
        assertEquals(2, constructorLineNodes.size());
        getNodeWithArtifactDataFromCollection(constructorLineNodes, new LineArtifactData("        // comment in constructor"));
        getNodeWithArtifactDataFromCollection(constructorLineNodes, new LineArtifactData("        System.out.println(\"constructor\");"));

        Node.Op stringMethod = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("stringMethod(int)"));
        List<Node.Op> stringMethodLineNodes = (List<Node.Op>) stringMethod.getChildren();
        assertEquals(3, stringMethodLineNodes.size());
        getNodeWithArtifactDataFromCollection(stringMethodLineNodes, new LineArtifactData("        // comment in method"));
        getNodeWithArtifactDataFromCollection(stringMethodLineNodes, new LineArtifactData("        String stringVariable = \"String\";"));
        getNodeWithArtifactDataFromCollection(stringMethodLineNodes, new LineArtifactData("        return stringVariable;"));

        Node.Op privateMethod = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("privateMethod()"));
        List<Node.Op> privateMethodLineNodes = (List<Node.Op>) privateMethod.getChildren();
        assertEquals(1, privateMethodLineNodes.size());
        getNodeWithArtifactDataFromCollection(privateMethodLineNodes, new LineArtifactData("        System.out.println(\"private method\");"));

        Node.Op staticMethod = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("staticMethod()"));
        List<Node.Op> staticMethodLineNodes = (List<Node.Op>) staticMethod.getChildren();
        assertEquals(1, staticMethodLineNodes.size());
        getNodeWithArtifactDataFromCollection(staticMethodLineNodes, new LineArtifactData("        return 1;"));
    }

    private void checkTestClassAFields(Node.Op fieldGroupNode){
        List<Node.Op> fieldNodes = (List<Node.Op>) fieldGroupNode.getChildren();
        assertEquals(2, fieldNodes.size());

        getNodeWithArtifactDataFromCollection(fieldNodes, new LineArtifactData("    private int intField;"));
        getNodeWithArtifactDataFromCollection(fieldNodes, new LineArtifactData("    private String stringField;"));
    }

    private void checkTestClassAEnums(Node.Op enumsGroupNode){
        List<Node.Op> enumsNodes = (List<Node.Op>) enumsGroupNode.getChildren();
        assertEquals(4, enumsNodes.size());

        getNodeWithArtifactDataFromCollection(enumsNodes, new LineArtifactData("    enum testEnum {"));
        getNodeWithArtifactDataFromCollection(enumsNodes, new LineArtifactData("        VALUEX,"));
        getNodeWithArtifactDataFromCollection(enumsNodes, new LineArtifactData("        VALUEY,"));
        getNodeWithArtifactDataFromCollection(enumsNodes, new LineArtifactData("        VALUEZ"));
    }

    private void checkTestClassB(Node.Op testClassBNode){
        List<Node.Op> groupNodes = (List<Node.Op>) testClassBNode.getChildren();
        assertEquals(5, groupNodes.size());

        Node.Op importGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("IMPORTS"));
        this.checkTestClassBImports(importGroupNode);
        Node.Op methodsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("METHODS"));
        this.checkTestClassBMethods(methodsGroupNode);
        Node.Op fieldsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("FIELDS"));
        this.checkTestClassBFields(fieldsGroupNode);
        Node.Op enumsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("ENUMS"));
        this.checkTestClassBEnums(enumsGroupNode);

        Node.Op innerClassNode = getNodeWithArtifactDataFromCollection(groupNodes, new ClassArtifactData("org.test.TestClassB.InnerClass"));
        this.checkTestClassBInnerClass(innerClassNode);
    }

    private void checkTestClassBImports(Node.Op importGroupNode){
        List<Node.Op> importNodes = (List<Node.Op>) importGroupNode.getChildren();
        assertEquals(2, importNodes.size());

        getNodeWithArtifactDataFromCollection(importNodes, new ImportArtifactData("import org.test.extern.x"));
        getNodeWithArtifactDataFromCollection(importNodes, new ImportArtifactData("import org.test.extern.y"));
    }

    private void checkTestClassBMethods(Node.Op methodsGroupNode){
        List<Node.Op> methodNodes = (List<Node.Op>) methodsGroupNode.getChildren();
        assertEquals(2, methodNodes.size());

        Node.Op constructor = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("TestClassB()"));
        List<Node.Op> constructorLineNodes = (List<Node.Op>) constructor.getChildren();
        assertEquals(1, constructorLineNodes.size());
        getNodeWithArtifactDataFromCollection(constructorLineNodes, new LineArtifactData("        System.out.println(\"constructor\");"));

        Node.Op stringMethod = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("stringMethod(int)"));
        List<Node.Op> stringMethodLineNodes = (List<Node.Op>) stringMethod.getChildren();
        assertEquals(2, stringMethodLineNodes.size());
        getNodeWithArtifactDataFromCollection(stringMethodLineNodes, new LineArtifactData("        String stringVariable = \"String\";"));
        getNodeWithArtifactDataFromCollection(stringMethodLineNodes, new LineArtifactData("        return stringVariable;"));
    }

    private void checkTestClassBFields(Node.Op fieldGroupNode){
        List<Node.Op> fieldNodes = (List<Node.Op>) fieldGroupNode.getChildren();
        assertEquals(2, fieldNodes.size());

        getNodeWithArtifactDataFromCollection(fieldNodes, new LineArtifactData("    private int intField;"));
        getNodeWithArtifactDataFromCollection(fieldNodes, new LineArtifactData("    private String stringField;"));
    }

    private void checkTestClassBEnums(Node.Op enumsGroupNode){
        List<Node.Op> enumsNodes = (List<Node.Op>) enumsGroupNode.getChildren();
        assertEquals(0, enumsNodes.size());
    }

    private void checkTestClassBInnerClass(Node.Op innerClassNode){
        List<Node.Op> groupNodes = (List<Node.Op>) innerClassNode.getChildren();
        assertEquals(3, groupNodes.size());

        Node.Op methodsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("METHODS"));
        this.checkInnerClassMethods(methodsGroupNode);
        Node.Op fieldsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("FIELDS"));
        this.checkInnerClassFields(fieldsGroupNode);
        Node.Op enumsGroupNode = getNodeWithArtifactDataFromCollection(groupNodes, new AbstractArtifactData("ENUMS"));
        this.checkInnerClassEnums(enumsGroupNode);
    }

    private void checkInnerClassMethods(Node.Op methodsGroupNode){
        List<Node.Op> methodNodes = (List<Node.Op>) methodsGroupNode.getChildren();
        assertEquals(2, methodNodes.size());

        Node.Op constructor = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("InnerClass()"));
        List<Node.Op> constructorLineNodes = (List<Node.Op>) constructor.getChildren();
        assertEquals(1, constructorLineNodes.size());
        getNodeWithArtifactDataFromCollection(constructorLineNodes, new LineArtifactData("            System.out.println(\"constructor inner class\");"));

        Node.Op innerMethod = getNodeWithArtifactDataFromCollection(methodNodes, new MethodArtifactData("innerClassMethod(boolean)"));
        List<Node.Op> innerMethodLineNodes = (List<Node.Op>) innerMethod.getChildren();
        assertEquals(1, innerMethodLineNodes.size());
        getNodeWithArtifactDataFromCollection(innerMethodLineNodes, new LineArtifactData("            System.out.println(\"inner class method\");"));
    }

    private void checkInnerClassFields(Node.Op fieldGroupNode){
        List<Node.Op> fieldNodes = (List<Node.Op>) fieldGroupNode.getChildren();
        assertEquals(1, fieldNodes.size());

        getNodeWithArtifactDataFromCollection(fieldNodes, new LineArtifactData("        private boolean booleanField;"));
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
}
