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
    private final LilypondSyntaxHighlighter.Style style;
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
        this.style = LilypondSyntaxHighlighter.styleFor(tad.getAction());

        String text = tad.getText();
        String[] nodeLines = text.split("\\n", -1);
        this.text = nodeLines[0];

        setupListeners();
        this.backgroundColor.set(backgroundColor);

        if (nodeLines.length > 1) {
            isLast = false;
            partOf = new Group(this);

            for (int i=1; i<nodeLines.length; i++) {
                partOf.add(new NodeTextBlock(node, backgroundColor, association, nodeLines[i], partOf, this.style));
            }
            partOf.blocks.get(partOf.size()-1).setLast();
        }
    }

    private NodeTextBlock(Node node, Color bgColor, Association association, String text, Group group, LilypondSyntaxHighlighter.Style style) {
        this.node = node;
        isFirst = false;
        isLast = false;
        this.association = association;
        this.text = text;
        this.partOf = group;
        this.style = style;

        setupListeners();
        this.backgroundColor.set(bgColor);
    }

    private void setupListeners() {
        mouseover.addListener((o, oldVal, newVal) -> applyMouseoverToGroup(newVal));
        highlighted.addListener((o, oldVal, newVal) -> {
            if (!mouseover.getValue()) updateOwnBackground();
        });
        backgroundColor.addListener((o, oldVal, newVal) -> {
            if (!mouseover.getValue()) updateOwnBackground();
        });
    }

    /**
     * Hover intentionally paints every block sharing this token's multi-line group at once (a
     * wrapped token should highlight as one unit no matter which line the mouse is actually over);
     * {@link #highlighted}/{@link #backgroundColor} changes only ever apply to the one block they
     * were set on, since the caller ({@code LilypondCodeViewer.FileView#highlightTree}) already
     * calls {@link #setHighlighted} on each affected block itself.
     */
    private void applyMouseoverToGroup(boolean entered) {
        List<NodeTextBlock> group = partOf != null ? partOf.getBlocks() : List.of(this);
        for (NodeTextBlock ntb : group) {
            if (entered) {
                ntb.background.set(new Background(new BackgroundFill(Color.rgb(50, 197, 255), null, null)));
            } else {
                ntb.updateOwnBackground();
            }
        }
    }

    /** Highlighted (selected in a reorder dialog, or navigated to) wins over the normal per-token
     * background color, e.g. an association's selection color -- both are just "this token is of
     * interest", but highlighted is the more specific, momentary signal. */
    private void updateOwnBackground() {
        Color color;
        if (Boolean.TRUE.equals(highlighted.getValue())) {
            color = Color.YELLOW;
        } else {
            color = backgroundColor.getValue();
            if (color == null || Color.TRANSPARENT.equals(color)) {
                color = Color.WHITE;
            }
        }
        background.set(new Background(new BackgroundFill(color, null, null)));
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

    LilypondSyntaxHighlighter.Style getStyle() {
        return style;
    }

    public int numLines() {
        return partOf == null ? 0 : partOf.size();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isFirst() {
        return isFirst;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLast() {
        return isLast;
    }

    private void setLast() {
        isLast = true;
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
