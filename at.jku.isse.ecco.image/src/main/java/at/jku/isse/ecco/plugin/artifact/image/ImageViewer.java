package at.jku.isse.ecco.plugin.artifact.image;

import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;

import java.util.HashSet;
import java.util.Set;

public class ImageViewer extends BorderPane implements ArtifactViewer {

	private ImageBufferWriter imageWriter = new ImageBufferWriter();

	@Override
	public void showTree(Node node) {
		Set<Node> nodes = new HashSet<>();
		nodes.add(node);

		if (node.getArtifact().getData() instanceof PluginArtifactData) {
			Image image = SwingFXUtils.toFXImage(this.imageWriter.write(nodes)[0], null);

			ImageView imageView = new ImageView();

			imageView.setImage(image);

			this.setCenter(imageView);
			this.setBackground(Background.EMPTY);
		} else if (node.getArtifact().getData() instanceof ImageArtifactData) {
			ImageArtifactData imageArtifactData = (ImageArtifactData) node.getArtifact().getData();

			if (imageArtifactData.getType().equals(ImageReader.TYPE_COLOR)) {
				this.setCenter(null);
				int[] colors = imageArtifactData.getValues();
				this.setBackground(new Background(new BackgroundFill(new Color(colors[1] / 255.0, colors[2] / 255.0, colors[3] / 255.0, colors[0] / 255.0), null, null)));
			}
		}
	}

	@Override
	public String getPluginId() {
		return ImagePlugin.class.getName();
	}

}
