package at.jku.isse.ecco.adapter.rust.translator;

import at.jku.isse.ecco.adapter.rust.data.FunctionArtifactData;
import at.jku.isse.ecco.adapter.rust.data.LineArtifactData;
import at.jku.isse.ecco.adapter.rust.data.StructArtifactData;
import at.jku.isse.ecco.adapter.rust.translator.structures.FunctionStructure;
import at.jku.isse.ecco.adapter.rust.translator.structures.StructStructure;
import at.jku.isse.ecco.adapter.rust.translator.structures.TraitStructure;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RustEccoTranslator {

    private final List<FunctionStructure> functionStructures;
    private final List<StructStructure> structStructures;
    private final List<TraitStructure> traitStructures;
    private final String[] codeLines;
    private final EntityFactory entityFactory;
    private final Path path;

    public RustEccoTranslator(String[] codeLines,
                              EntityFactory entityFactory,
                              Path path) {
        this.codeLines = codeLines;
        this.entityFactory = entityFactory;
        this.functionStructures = new ArrayList<>();
        this.structStructures = new ArrayList<>();
        this.traitStructures = new ArrayList<>();
        this.path = path;
    }

    public void addFunctionStructure(int start, int end, String functionSignature) {
        this.functionStructures.add(new FunctionStructure(start, end, functionSignature));
    }

    public void addChildrenToPluginNode(Node.Op pluginNode) {
        this.sortFunctionStructures();
        this.sortStructStructures();
        this.sortTraitStructures();

        for (TraitStructure traitStructure : this.traitStructures) {
            Node.Op traitNode = this.createTraitNode(traitStructure);
            pluginNode.addChild(traitNode);
        }

        for (StructStructure structStructure : this.structStructures) {
            Node.Op structNode = this.createStructNode(structStructure);
            pluginNode.addChild(structNode);
        }

        int startLine = 1;
        for (FunctionStructure functionStructure : this.functionStructures) {
            this.addLineNodes(pluginNode, startLine, functionStructure.getStartLine() - 1);
            Node.Op functionNode = this.createFunctionNode(functionStructure);
            pluginNode.addChild(functionNode);
            startLine = functionStructure.getEndLine() + 1;
        }
        this.addLineNodes(pluginNode, startLine, this.codeLines.length);

    }

    private void sortFunctionStructures() {
        Comparator<FunctionStructure> compareByStart = Comparator.comparingInt(FunctionStructure::getStartLine);
        this.functionStructures.sort(compareByStart);
    }

    private void addLineNodes(Node.Op parentNode, int startLine, int endLine) {
        for (int i = startLine; i <= endLine; i++) {
            String codeLine = this.codeLines[i - 1];
            if (codeLine.isEmpty()) {
                continue;
            }

            Artifact.Op<LineArtifactData> lineArtifactData = this.entityFactory.createArtifact(new LineArtifactData(codeLine));
            Node.Op lineNode = this.entityFactory.createNode(lineArtifactData);
            parentNode.addChild(lineNode);
        }
    }

    private Node.Op createFunctionNode(FunctionStructure functionStructure) {
        Artifact.Op<FunctionArtifactData> data = this.entityFactory.createArtifact(new FunctionArtifactData(functionStructure.getFunctionSignature()));
        Node.Op functionNode = this.entityFactory.createOrderedNode(data);
        this.addLineNodes(functionNode, functionStructure.getStartLine(), functionStructure.getEndLine());
        return functionNode;
    }

    private Node.Op createStructNode(StructStructure structStructure) {
        Artifact.Op<StructArtifactData> data = this.entityFactory.createArtifact(new StructArtifactData(structStructure.getStructSignature()));
        Node.Op structNode = this.entityFactory.createOrderedNode(data);
        this.addLineNodes(structNode, structStructure.getStartLine(), structStructure.getEndLine());
        return structNode;

    }

    public void addStructStructure(int line, int line1, String structSignature) {
        this.structStructures.add(new StructStructure(line, line1, structSignature));
    }

    private void sortStructStructures() {
        Comparator<StructStructure> compareByStart = Comparator.comparingInt(StructStructure::getStartLine);
        this.structStructures.sort(compareByStart);
    }

    public void addTraitStructure(int line, int line1, String traitSignature) {
        this.traitStructures.add(new TraitStructure(line, line1, traitSignature));
    }

    private Node.Op createTraitNode(TraitStructure traitStructure) {
        Artifact.Op<StructArtifactData> data = this.entityFactory.createArtifact(new StructArtifactData(traitStructure.getTraitSignature()));
        Node.Op traitNode = this.entityFactory.createOrderedNode(data);
        this.addLineNodes(traitNode, traitStructure.getStartLine(), traitStructure.getEndLine());
        return traitNode;
    }

    private void sortTraitStructures() {
        Comparator<TraitStructure> compareByStart = Comparator.comparingInt(TraitStructure::getStartLine);
        this.traitStructures.sort(compareByStart);
    }


}
