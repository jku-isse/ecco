package at.jku.isse.ecco.adapter.lilypond.view;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;

public class TextBlockLabel extends Label {
    private final static PseudoClass HIGHLIGHT_PSEUDO_CLASS = PseudoClass.getPseudoClass("highlight");

    public TextBlockLabel(String text) {
        super(text);
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
