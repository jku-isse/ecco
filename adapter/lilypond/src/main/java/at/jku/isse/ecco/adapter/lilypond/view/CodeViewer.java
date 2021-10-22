package at.jku.isse.ecco.adapter.lilypond.view;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.data.context.BaseContextArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public class CodeViewer extends BorderPane implements ArtifactViewer {

	private final TextFlow textFlow;
	private final TextArea taInfo;

	public CodeViewer() {
		textFlow = new TextFlow();
		textFlow.setLineSpacing(1);

		ScrollPane sp = new ScrollPane(textFlow);
		this.setCenter(sp);
		BorderPane.setAlignment(sp, Pos.TOP_LEFT);

		taInfo = new TextArea();
		taInfo.setMinHeight(80);
		taInfo.setWrapText(true);
		this.setBottom(taInfo);

		ArtifactDataLabelNode.setInfoArea(taInfo);
	}

	@Override
	public void showTree(Node node) {
		return;
		/*
		textFlow.getChildren().clear();
		taInfo.clear();

		if (node.getArtifact().getData() instanceof PluginArtifactData) {
			for (Node n : node.getChildren()) {
				appendNodes(n, textFlow);
			}
		} else {
			appendNodes(node, textFlow);
		}
		 */
	}

	@Override
	public String getPluginId() {
		return LilypondPlugin.class.getName();
	}

	private void appendNodes(Node node, TextFlow tf) {
		ArtifactData d = node.getArtifact().getData();
		if (d instanceof BaseContextArtifactData) {
			for (Node cn : node.getChildren()) {
				appendNodes(cn, tf);
			}

		} else if (d instanceof DefaultTokenArtifactData dad) {
			String text = dad.getText().concat(dad.getPostWhitespace());
			Association a = node.getArtifact().getContainingNode() != null
					? node.getArtifact().getContainingNode().getContainingAssociation()
					: null;

			String[] lines = text.split("\\n", -1);
			for (int i=0; i<lines.length; i++) {
				ArtifactDataLabelNode ln = new ArtifactDataLabelNode(lines[i], a);
				Map<String, Object> props = node.getProperties();

				try {
					Method m = node.getClass().getDeclaredMethod("colorProperty");
					ln.getBackgroundColorProperty().bind((ObjectProperty<Color>)m.invoke(node));

				} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
					// silent
				}
				tf.getChildren().add(ln);
				if (i < lines.length-1) {
					tf.getChildren().add(new Text("\n"));
				}
			}
		}
	}

}