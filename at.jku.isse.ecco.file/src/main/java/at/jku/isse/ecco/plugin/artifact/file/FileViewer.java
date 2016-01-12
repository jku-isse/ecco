package at.jku.isse.ecco.plugin.artifact.file;

import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.tree.Node;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class FileViewer extends BorderPane implements ArtifactViewer {

	@Override
	public void showTree(Node node) {
		VBox box = new VBox();
		box.setPadding(new Insets(10, 10, 10, 10));

		FileArtifactData fad = (FileArtifactData) node.getAllChildren().get(0).getArtifact().getData();

		box.getChildren().add(new Label("Identifier: " + fad.getIdentifier()));
		box.getChildren().add(new Label("Checksum: " + fad.getHexChecksum()));
		box.getChildren().add(new Label("Path: " + fad.getPath().toString()));
		box.getChildren().add(new Label("Size: " + String.valueOf(fad.getData().length) + " bytes"));

		this.setCenter(box);
	}

	@Override
	public String getPluginId() {
		return FilePlugin.class.getName();
	}

}
