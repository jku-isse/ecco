package at.jku.isse.ecco.adapter.file;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class FileViewer extends BorderPane implements ArtifactViewer {

	@Override
	public void showTree(Node node) {
		VBox box = new VBox();

		if (node.getArtifact().getData() instanceof PluginArtifactData) {
			for (Node fileNode : node.getChildren()) {
				box.getChildren().add(this.createFileBox(fileNode));
			}
		} else if (node.getArtifact().getData() instanceof FileArtifactData) {
			box.getChildren().add(this.createFileBox(node));
		}

		this.setCenter(box);
	}


	private javafx.scene.Node createFileBox(Node node) {
		VBox fileBox = new VBox();
		fileBox.setPadding(new Insets(10, 10, 10, 10));

		FileArtifactData fad = (FileArtifactData) node.getArtifact().getData();

		fileBox.getChildren().add(new Label("Identifier: " + fad.getIdentifier()));
		fileBox.getChildren().add(new Label("Checksum: " + fad.getHexChecksum()));
		fileBox.getChildren().add(new Label("Path: " + fad.getPath().toString()));
		fileBox.getChildren().add(new Label("Size: " + String.valueOf(fad.getData().length) + " bytes"));

		return fileBox;
	}


	@Override
	public String getPluginId() {
		return FilePlugin.class.getName();
	}

}
