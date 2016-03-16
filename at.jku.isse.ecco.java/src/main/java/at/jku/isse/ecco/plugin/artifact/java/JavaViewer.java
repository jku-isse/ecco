package at.jku.isse.ecco.plugin.artifact.java;

import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.layout.BorderPane;

public class JavaViewer extends BorderPane implements ArtifactViewer {

	@Override
	public String getPluginId() {
		return null;
	}

	@Override
	public void showTree(Node node) {

	}

}
