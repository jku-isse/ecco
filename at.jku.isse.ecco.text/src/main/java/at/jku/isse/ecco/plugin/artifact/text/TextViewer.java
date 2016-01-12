package at.jku.isse.ecco.plugin.artifact.text;

import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;

import java.util.HashSet;
import java.util.Set;

public class TextViewer extends BorderPane implements ArtifactViewer {

	private TextStringWriter textWriter = new TextStringWriter();

	@Override
	public void showTree(Node node) {
		TextArea textField = new TextArea();

		Set<Node> nodes = new HashSet<>();
		nodes.add(node);
		textField.setText(this.textWriter.write(nodes)[0]);
		textField.setEditable(false);
		textField.prefWidthProperty().bind(this.widthProperty());
		textField.prefHeightProperty().bind(this.heightProperty());

		this.setCenter(textField);
	}

	@Override
	public String getPluginId() {
		return TextPlugin.class.getName();
	}

}
