package at.jku.isse.ecco.adapter.lilypond.view;

import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import javafx.beans.property.*;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;

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
    private final BooleanProperty highlighted = new SimpleBooleanProperty(false);
    private final BooleanProperty mouseover = new SimpleBooleanProperty(false);
    private final ObjectProperty<Background> background = new SimpleObjectProperty<>();
    private final ObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>();

    public NodeTextBlock(Node node, Color backgroundColor) {
        this.node = node;

        if (node.getArtifact() == null || node.getArtifact().getData() == null ||
                !(node.getArtifact().getData() instanceof DefaultTokenArtifactData tad)) {
            throw new IllegalArgumentException("expected instance of DefaultTokenArtifactData");
        }

        association = node.getArtifact().getContainingNode() != null
                ? node.getArtifact().getContainingNode().getContainingAssociation()
                : null;

        String text = tad.getText().concat(" "); // TODO: whitspace
        String[] nodeLines = text.split("\\n", -1);
        this.text = nodeLines[0];

        setupListeners();
        this.backgroundColor.set(backgroundColor);

        if (nodeLines.length > 1) {
            isLast = false;
            partOf = new Group(this);

            for (int i=1; i<nodeLines.length; i++) {
                partOf.add(new NodeTextBlock(node, backgroundColor, association, nodeLines[i], partOf));
            }
            partOf.blocks.get(partOf.size()-1).setLast(true);
        }
    }

    private NodeTextBlock(Node node, Color bgColor, Association association, String text, Group group) {
        this.node = node;
        isFirst = false;
        isLast = false;
        this.association = association;
        this.text = text;
        this.partOf = group;

        setupListeners();
        this.backgroundColor.set(bgColor);
    }

    private void setupListeners() {
        mouseover.addListener((o, oldVal, newVal) -> {
            Background bg = newVal ?
                    new Background(new BackgroundFill(Color.rgb(50, 197, 255), null, null)) :
                    new Background(new BackgroundFill(backgroundColor.getValue(), null, null));
            if (partOf != null) {
                for (NodeTextBlock ntb : partOf.getBlocks()) {
                    ntb.background.set(bg);
                }
            } else {
                background.set(bg);
            }
        });

        backgroundColor.addListener((o, oldVal, newVal) -> {
            if (!mouseover.getValue()) {
                if (newVal == null || newVal == Color.TRANSPARENT) {
                    newVal = Color.WHITE;
                }
                background.set(new Background(new BackgroundFill(newVal, null, null)));
            }
        });
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
        return partOf == null ? null : partOf.getBlocks();
    }

    public BooleanProperty highlightedProperty() {
        return highlighted;
    }

    public BooleanProperty mouseoverProperty() { return mouseover; }

    public void setHighlighted(boolean flag) {
        highlighted.set(flag);
    }

    public ReadOnlyObjectProperty<Background> backgroundProperty() { return background; }

    public ObjectProperty<Color> backgroundColor() { return backgroundColor; }

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
