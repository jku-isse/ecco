package at.jku.isse.ecco.adapter.rust.translator;

import at.jku.isse.ecco.adapter.rust.data.FunctionArtifactData;
import at.jku.isse.ecco.adapter.rust.data.LineArtifactData;
import at.jku.isse.ecco.adapter.rust.data.StructArtifactData;
import at.jku.isse.ecco.adapter.rust.data.TraitArtifactData;
import at.jku.isse.ecco.adapter.rust.translator.structures.Structure;
import at.jku.isse.ecco.adapter.rust.translator.structures.Type;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RustEccoTranslator {
    private final List<Structure> structures;
    private final String[] codeLines;
    private final EntityFactory entityFactory;
    private final Path path;

    public RustEccoTranslator(String[] codeLines,
                              EntityFactory entityFactory,
                              Path path) {
        this.codeLines = codeLines;
        this.entityFactory = entityFactory;
        this.path = path;
    }

    public void addChildrenToPluginNode(Node.Op pluginNode) {
        int startLine = 1;
        List<Structure> sortedStructures = this.structures.stream()
                .sorted(Comparator.comparingInt(Structure::getStartLine))
                .collect(Collectors.toList());
        for (Structure structure : sortedStructures) {
            this.addLineNodes(pluginNode, startLine, structure.getStartLine() - 1);
            Node.Op node = this.createNode(structure);
            pluginNode.addChild(node);
            startLine = structure.getEndLine() + 1;
        }
        this.addLineNodes(pluginNode, startLine, this.codeLines.length);
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

    private Node.Op createFunctionNode(Structure structure) {
        Artifact.Op<FunctionArtifactData> data = this.entityFactory.createArtifact(new FunctionArtifactData(structure.getContent()));
        Node.Op functionNode = this.entityFactory.createOrderedNode(data);
        this.addLineNodes(functionNode, structure.getStartLine(), structure.getEndLine());
        return functionNode;
    }

    private Node.Op createStructNode(Structure structure) {
        Artifact.Op<StructArtifactData> data = this.entityFactory.createArtifact(new StructArtifactData(structure.getContent()));
        Node.Op structNode = this.entityFactory.createOrderedNode(data);
        this.addLineNodes(structNode, structure.getStartLine(), structure.getEndLine());
        return structNode;

    }

    private Node.Op createTraitNode(Structure structure) {
        Artifact.Op<StructArtifactData> data = this.entityFactory.createArtifact(new TraitArtifactData(structure.getContent()));
        Node.Op traitNode = this.entityFactory.createOrderedNode(data);
        this.addLineNodes(traitNode, structure.getStartLine(), structure.getEndLine());
        return traitNode;
    }

    public void addStructure(int startLine, int endLine, String content, Type type) {
        this.structures.add(new Structure(startLine, endLine, content, type));
    }

}
