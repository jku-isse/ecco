package at.jku.isse.ecco.adapter.lilypond.view;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;

public class TextBlockLabel extends Label {
    private final static PseudoClass HIGHLIGHT_PSEUDO_CLASS = PseudoClass.getPseudoClass("highlight");

    private final NodeTextBlock textBlock;

    public TextBlockLabel(NodeTextBlock block) {
        super(block.getText());
        textBlock = block;
        setBackground(new Background(new BackgroundFill(
                block.backgroundColorProperty().getValue(), null, null)));
    }

    public NodeTextBlock getTextBlock() {
        return textBlock;
    }

    private final BooleanProperty highlighted = new BooleanPropertyBase() {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(HIGHLIGHT_PSEUDO_CLASS, get());
        }

        @Override
        public Object getBean() {
            return TextBlockLabel.this;
        }

        @Override
        public String getName() {
            return "highlighted";
        }
    };

    public BooleanProperty highlightedProperty() {
        return highlighted;
    }
}
