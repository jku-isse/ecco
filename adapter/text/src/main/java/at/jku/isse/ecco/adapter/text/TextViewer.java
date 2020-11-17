package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

public class TextViewer extends BorderPane implements ArtifactViewer {

	private TextStringWriter textWriter = new TextStringWriter();

	@Override
	public void showTree(Node node) {
		Set<Node> nodes = new HashSet<>();
		nodes.add(node);

		if (node.getArtifact().getData() instanceof PluginArtifactData) {
			TextArea textField = new TextArea();
			textField.setText(this.textWriter.write(nodes)[0]);
			textField.setEditable(false);
			textField.prefWidthProperty().bind(this.widthProperty());
			textField.prefHeightProperty().bind(this.heightProperty());

			textField.caretPositionProperty().addListener((observable, oldValue, newValue) -> {
				int caretPos = newValue.intValue();
				String text = textField.getText().substring(0, caretPos);
				int linePos = text.lastIndexOf(System.lineSeparator());
				int col = linePos == -1 ? caretPos : caretPos - linePos;
				int line = new StringTokenizer(text, System.lineSeparator()).countTokens();

				System.out.println("LINE: " + line + "; COL: " + col);
			});

			textField.selectionProperty().addListener((observable, oldValue, newValue) -> {
				{
					int caretPos = newValue.getStart();
					String text = textField.getText().substring(0, caretPos);
					int linePos = text.lastIndexOf(System.lineSeparator());
					int col = linePos == -1 ? caretPos : caretPos - linePos;
					int line = new StringTokenizer(text, System.lineSeparator()).countTokens();

					System.out.println("START -> LINE: " + line + "; COL: " + col);
				}
				{
					int caretPos = newValue.getEnd();
					String text = textField.getText().substring(0, caretPos);
					int linePos = text.lastIndexOf(System.lineSeparator());
					int col = linePos == -1 ? caretPos : caretPos - linePos;
					int line = new StringTokenizer(text, System.lineSeparator()).countTokens();

					System.out.println("END -> LINE: " + line + "; COL: " + col);
				}
			});

			this.setCenter(textField);
		} else if (node.getArtifact().getData() instanceof LineArtifactData) {
			LineArtifactData lineArtifactData = (LineArtifactData) node.getArtifact().getData();

			TextArea textField = new TextArea();
			textField.setText(lineArtifactData.getLine());
			textField.setEditable(false);
			textField.prefWidthProperty().bind(this.widthProperty());
			textField.prefHeightProperty().bind(this.heightProperty());

			this.setCenter(textField);
		}
	}

	@Override
	public String getPluginId() {
		return TextPlugin.class.getName();
	}

}
