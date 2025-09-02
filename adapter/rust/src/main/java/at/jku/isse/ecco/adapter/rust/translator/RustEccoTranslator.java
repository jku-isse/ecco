package at.jku.isse.ecco.adapter.rust.translator;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.rust.data.*;
import at.jku.isse.ecco.adapter.rust.translator.structures.Structure;
import at.jku.isse.ecco.adapter.rust.translator.structures.Type;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        this.structures = new ArrayList<>();
    }

    public void addChildrenToPluginNode(Node.Op pluginNode) {
        int startLine = 1;
        List<Structure> sortedStructures = this.structures.stream()
                .sorted(Comparator.comparingInt(Structure::getStartLine))
                .collect(Collectors.toList());
        for (Structure structure : sortedStructures) {
            // TODO attributes are added as an lineNode for itself, meaning it gets duplicated in the function/struct/trait node
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

    private Node.Op createNode(Structure structure) {
        ArtifactData data;
        Type type = structure.getType();
        switch (type) {
            case FUNCTION:
                data = new FunctionArtifactData(structure.getContent());
                break;
            case STRUCT:
                data = new StructArtifactData(structure.getContent());
                break;
            case TRAIT:
                data = new TraitArtifactData(structure.getContent());
                break;
            case IMPLEMENTATION:
                data = new ImplementationArtifactData(structure.getContent());
                break;
            default:
                throw new EccoException("Unexpected value of node type: " + type);
        }
        Optional<Node.Op> attributeNode = this.GetAttributesAndCreateNode(structure);

        Artifact.Op<ItemArtifactData> item = this.entityFactory.createArtifact(new ItemArtifactData());
        Node.Op itemNode = this.entityFactory.createNode(item);

        Artifact.Op<? extends ArtifactData> artifact = this.entityFactory.createArtifact(data);
        Node.Op node = this.entityFactory.createOrderedNode(data);

        attributeNode.ifPresent(node::addChild);
        itemNode.addChild(node);
        this.addLineNodes(node, structure.getStartLine(), structure.getEndLine());
        return itemNode;
    }

    public Optional<Node.Op> GetAttributesAndCreateNode(Structure structure) {
        String attributes = structure.getAttributes();
        Artifact.Op<AttributeArtifactData> attributeArtifact = this.entityFactory.createArtifact(new AttributeArtifactData(attributes));
        return Optional.ofNullable(this.entityFactory.createOrderedNode(attributeArtifact));
    }

    public void addStructure(int startLine, int endLine, String content, Type type) {
        this.structures.add(new Structure(startLine, endLine, content, type, ""));
    }

    public void addStructure(int startLine, int endLine, String content, Type type, String attributes) {
        this.structures.add(new Structure(startLine, endLine, content, type, attributes));
    }



}
