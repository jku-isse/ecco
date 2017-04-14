package at.jku.isse.ecco.plugin.artifact.cpp;

import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.layout.BorderPane;

public class CppViewer extends BorderPane implements ArtifactViewer {

	@Override
	public String getPluginId() {
		return CppPlugin.class.getName();
	}

	@Override
	public void showTree(Node node) {

	}

}
