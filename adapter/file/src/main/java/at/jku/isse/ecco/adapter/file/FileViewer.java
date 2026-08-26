package at.jku.isse.ecco.adapter.file;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * Also the association-preview viewer for {@link FilePlugin} content (a single opaque blob per
 * file, not line/token-granular) - {@code KnowledgeGraphView}'s hover/detached preview silently
 * fell back to a bare label for any association handled by this plugin (the generic fallback
 * adapter for a file type with no dedicated one) since {@link FileModule} never registered a
 * viewer for the {@link at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer} multibinder it
 * looks in, unlike every other adapter's viewer.
 */
public class FileViewer extends BorderPane implements AssociationInfoArtifactViewer {

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

	/**
	 * No-op: a {@link FileArtifactData} node is a single opaque blob (identifier/checksum/path/size,
	 * see {@link #createFileBox}), not line/token-granular content, so there is nothing here for a
	 * per-association selection color to highlight - same rationale the interface's own default
	 * gives for {@code setShowDetailsPanel}.
	 */
	@Override
	public void setAssociationInfos(Collection<AssociationInfo> associationInfos) {
	}

}
