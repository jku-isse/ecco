package at.jku.isse.ecco.plugin.artifact.image;

import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.tree.Node;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;

import java.util.HashSet;
import java.util.Set;

public class ImageViewer extends BorderPane implements ArtifactViewer {

	private ImageBufferWriter imageWriter = new ImageBufferWriter();

	@Override
	public void showTree(Node node) {
		Set<Node> nodes = new HashSet<>();
		nodes.add(node);
		Image image = SwingFXUtils.toFXImage(this.imageWriter.write(nodes)[0], null);

		ImageView imageView = new ImageView();

		imageView.setImage(image);

		this.setCenter(imageView);
	}

	@Override
	public String getPluginId() {
		return ImagePlugin.class.getName();
	}

}
