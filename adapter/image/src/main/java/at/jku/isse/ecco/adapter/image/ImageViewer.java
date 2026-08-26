package at.jku.isse.ecco.adapter.image;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Also the association-preview viewer for {@link ImagePlugin} content - same gap/fix as
 * {@code at.jku.isse.ecco.adapter.file.FileViewer}: {@code KnowledgeGraphView}'s hover/detached
 * preview silently fell back to a bare label for every image-backed association since
 * {@link ImageModule} never registered a viewer for the
 * {@link at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer} multibinder it looks in.
 */
public class ImageViewer extends BorderPane implements AssociationInfoArtifactViewer {

	private FxImageWriter imageWriter = new FxImageWriter();

	@Override
	public void showTree(Node node) {
		Set<Node> nodes = new HashSet<>();
		nodes.add(node);

		if (node.getArtifact().getData() instanceof PluginArtifactData) {
			Image image = this.imageWriter.write(nodes)[0];

			ImageView imageView = new ImageView();

			imageView.setImage(image);


			Button saveButton = new Button("Save");
			saveButton.setOnAction(event -> {
				FileChooser fileChooser = new FileChooser();
				File selectedFile = fileChooser.showSaveDialog(this.getScene().getWindow());

				if (selectedFile != null) {
					String name = selectedFile.getName();
					String type = "png";
					int index = name.indexOf(".");
					if (index != -1 && index < name.length() - 1)
						type = name.substring(name.lastIndexOf(".") + 1);
					try {
						boolean result = ImageIO.write(SwingFXUtils.fromFXImage(image, null), type, selectedFile);
						if (!result)
							throw new EccoException("Error writing image file: Unknown format.");
					} catch (IOException e) {
						throw new EccoException("Error writing image file.", e);
					}
				}
			});


			this.setTop(saveButton);
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

	/**
	 * No-op: this viewer renders either the whole reconstructed image or a single pixel/region
	 * swatch, neither of which has a per-association selection color to apply - same rationale as
	 * {@code FileViewer}'s identical no-op.
	 */
	@Override
	public void setAssociationInfos(Collection<AssociationInfo> associationInfos) {
	}

}
