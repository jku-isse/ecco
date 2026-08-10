package at.jku.isse.ecco.adapter.c.view;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * One rendered, indented line of the reconstruction shown by {@link CCodeViewer}. A
 * {@code LineArtifactData}'s stored text is already a full source line (including braces, e.g. a
 * function's opening "{" is itself a line), so there is usually a 1:1 mapping between an artifact
 * node and a rendered line here - no synthetic lines are needed, unlike the Java adapter's viewer -
 * except that a single artifact whose text carries an embedded newline is fanned out across several
 * consecutive CCodeLine rows by {@link CCodeViewer#addLine}.
 */
public class CCodeLine {

	private final Node node;
	private final Association association;
	private final String text;
	private final int indent;
	private final List<CSyntaxHighlighter.Token> tokens;

	private final ObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>(Color.WHITE);
	private final ObjectProperty<Background> background = new SimpleObjectProperty<>();

	public CCodeLine(Node node, Association association, String text, int indent, List<CSyntaxHighlighter.Token> tokens) {
		this.node = node;
		this.association = association;
		this.text = text;
		this.indent = indent;
		this.tokens = tokens;

		this.backgroundColor.addListener((o, oldVal, newVal) -> {
			Color color = newVal == null || newVal.equals(Color.TRANSPARENT) ? Color.WHITE : newVal;
			this.background.set(new Background(new BackgroundFill(color, null, null)));
		});
		this.background.set(new Background(new BackgroundFill(Color.WHITE, null, null)));
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

	public int getIndent() {
		return indent;
	}

	public List<CSyntaxHighlighter.Token> getTokens() {
		return tokens;
	}

	public ObjectProperty<Color> backgroundColor() {
		return backgroundColor;
	}

	public ObjectProperty<Background> backgroundProperty() {
		return background;
	}
}
