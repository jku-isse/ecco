package at.jku.isse.ecco.adapter.java.view;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * One rendered, indented line of the pretty-printed reconstruction shown by {@link JavaCodeViewer}.
 * Unlike lilypond's per-token {@code NodeTextBlock}, a Java artifact's stored text already
 * represents a whole statement/declaration, so there is usually a 1:1 mapping between an artifact
 * node and a rendered line here (synthetic lines such as a closing "}" have a null node/association)
 * - except that a single artifact whose text carries an embedded newline is fanned out across
 * several consecutive JavaCodeLine rows by {@link JavaCodeViewer#addLine}.
 */
public class JavaCodeLine {

	private final Node node;
	private final Association association;
	private final String text;
	private final int indent;
	private final List<JavaSyntaxHighlighter.Token> tokens;

	private final ObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>(Color.WHITE);
	private final ObjectProperty<Background> background = new SimpleObjectProperty<>();

	public JavaCodeLine(Node node, Association association, String text, int indent, List<JavaSyntaxHighlighter.Token> tokens) {
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

	public List<JavaSyntaxHighlighter.Token> getTokens() {
		return tokens;
	}

	public ObjectProperty<Color> backgroundColor() {
		return backgroundColor;
	}

	public ObjectProperty<Background> backgroundProperty() {
		return background;
	}
}
