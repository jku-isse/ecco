package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.tree.Node;

public class NodeArtifactEntry {
    private final Node node;
    private final JavaTreeArtifactData artifact;

    private NodeArtifactEntry(Node node, JavaTreeArtifactData artifact) {
        this.node = node;
        this.artifact = artifact;
    }

    public Node getNode() {
        return node;
    }

    public JavaTreeArtifactData getArtifact() {
        return artifact;
    }

    public static NodeArtifactEntry fromNode(Node node) {
        final ArtifactData data = node.getArtifact().getData();
        if (!(data instanceof JavaTreeArtifactData))
            return null;
        return new NodeArtifactEntry(node, (JavaTreeArtifactData) data);
    }

    @Override
    public String toString() {
        return artifact.toString();
    }
}
