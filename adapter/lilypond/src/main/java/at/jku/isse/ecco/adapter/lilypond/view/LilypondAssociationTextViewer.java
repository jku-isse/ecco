package at.jku.isse.ecco.adapter.lilypond.view;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.data.context.BaseContextArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextFlow;

public class LilypondAssociationTextViewer extends BorderPane implements ArtifactViewer {

	@Override
	public void showTree(Node node) {
return;
/*
		this.setBackground(new Background(new BackgroundFill(Color.WHITE, null, null)));
		TextFlow tf = new TextFlow();
		ScrollPane sp = new ScrollPane(tf);
		this.setCenter(sp);
		BorderPane.setAlignment(sp, Pos.TOP_LEFT);

		TextArea ta = new TextArea();
		ta.setMinHeight(80);
		this.setBottom(ta);

		ArtifactDataTextNode.setInfoArea(ta);

		if (node.getArtifact().getData() instanceof PluginArtifactData) {
			for (Node n : node.getChildren()) {
				appendNodes(n, tf);
			}
		} else {
			appendNodes(node, tf);
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

		} else if (d instanceof DefaultTokenArtifactData) {
			DefaultTokenArtifactData dad = (DefaultTokenArtifactData)d;
			ArtifactDataTextNode tn = new ArtifactDataTextNode(dad.getText().concat(dad.getPostWhitespace()));
			if (node.getArtifact().getContainingNode() != null) {
				Association a = node.getArtifact().getContainingNode().getContainingAssociation();
				tn.setAssociation(a);
			}
			tf.getChildren().add(tn);
		}
	}

}