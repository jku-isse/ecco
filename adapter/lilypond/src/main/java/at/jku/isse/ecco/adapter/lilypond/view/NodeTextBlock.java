package at.jku.isse.ecco.adapter.lilypond.view;

import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.Background;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NodeTextBlock {
    private final Node node;
    private final Association association;
    private final String text;
    private Group partOf;
    private boolean isFirst = true;
    private boolean isLast = true;

    public NodeTextBlock(Node node) {
        this.node = node;

        if (node.getArtifact() == null || node.getArtifact().getData() == null ||
                !(node.getArtifact().getData() instanceof DefaultTokenArtifactData tad)) {
            throw new IllegalArgumentException("expected instance of DefaultTokenArtifactData");
        }

        association = node.getArtifact().getContainingNode() != null
                ? node.getArtifact().getContainingNode().getContainingAssociation()
                : null;

        String text = tad.getText().concat(tad.getPostWhitespace());
        String[] nodeLines = text.split("\\n", -1);
        this.text = nodeLines[0];

        if (nodeLines.length > 1) {
            isLast = false;
            partOf = new Group(this);

            for (int i=1; i<nodeLines.length; i++) {
                partOf.add(new NodeTextBlock(node, association, nodeLines[i], partOf));
            }
            partOf.blocks.get(partOf.size()-1).setLast(true);
        }
    }

    private NodeTextBlock(Node node, Association association, String text, Group group) {
        this.node = node;
        isFirst = false;
        isLast = false;
        this.association = association;
        this.text = text;
        this.partOf = group;
    }

    public Node getNode() {
        return node;
    }

    public Association getAssociation() {
        return association;
    }

    public String getText() {
        return text;
    }

    public int numLines() {
        return partOf == null ? 0 : partOf.size();
    }

    public boolean isFirst() {
        return isFirst;
    }

    public boolean isLast() {
        return isLast;
    }

    private void setLast(boolean f) {
        isLast = f;
    }

    public List<NodeTextBlock> getGroup() {
        assert partOf != null;

        return partOf.getBlocks();
    }

    private static class Group {
        private final ArrayList<NodeTextBlock> blocks;

        public Group(NodeTextBlock block) {
            blocks = new ArrayList<>();
            add(block);
        }

        public void add(NodeTextBlock block) {
            blocks.add(block);
        }

        public List<NodeTextBlock> getBlocks() {
            return Collections.unmodifiableList(blocks);
        }

        public int size() {
            return blocks.size();
        }
    }
}
