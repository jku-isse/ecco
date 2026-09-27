package at.jku.isse.ecco.adapter.image;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class FxImageWriter implements ArtifactWriter<Set<Node>, Image> {

	private int backgroundColor = 0x00ffffff;
	private boolean enableBlending = true;

	@Override
	public String getPluginId() {
		return ImagePlugin.class.getName();
	}

	@Override
	public Image[] write(Set<Node> nodes) {
		return this.write(null, nodes);
	}

	@Override
	public Image[] write(Image base, Set<Node> nodes) {
		List<Image> output = new ArrayList<>();

		for (Node pluginNode : nodes) {
			if (!(pluginNode.getArtifact().getData() instanceof PluginArtifactData)) {
				throw new EccoException("Top nodes must be plugin nodes!");
			} else {
				Image outputImage = createImage(pluginNode, this.backgroundColor, this.enableBlending);
				output.add(outputImage);
			}
		}

		return output.toArray(new Image[output.size()]);
	}

	private Collection<WriteListener> listeners = new ArrayList<>();

	@Override
	public void addListener(WriteListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(WriteListener listener) {
		this.listeners.remove(listener);
	}

	private static Image createImage(Node pluginNode, int backgroundColor, boolean enableBlending) {
		Node imageNode = null;
		for (Node node : pluginNode.getChildren()) {
			if ((node.getArtifact().getData() instanceof ImageArtifactData) && ((ImageArtifactData) node.getArtifact().getData()).getType().equals(ImageReader.TYPE_IMAGE)) {
				imageNode = node;
			}
		}
		if (imageNode == null)
			throw new EccoException("There must be exactly one image node!");
		// ImageArtifact imageArtifact = (ImageArtifact) fileNode.getAllChildren().iterator().next().getArtifact();
		ImageArtifactData imageArtifact = (ImageArtifactData) imageNode.getArtifact().getData();
		int width = imageArtifact.getValues()[0];
		int height = imageArtifact.getValues()[1];

		int defaultColor = 0;
		{
			int alpha = 128;
			int red = 0;
			int green = 255;
			int blue = 0;
			defaultColor = (blue & 0x000000ff) | ((green << 8) & 0x0000ff00) | ((red << 16) & 0x00ff0000) | ((alpha << 24) & 0xff000000);

			defaultColor = backgroundColor;
		}

		WritableImage outputImage = new WritableImage(width, height);
		PixelWriter pixelWriter = outputImage.getPixelWriter();

		// TODO: include image metadata in artifacts, like type or color model

		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				//outputImage.setRGB(i, j, defaultColor);
				pixelWriter.setArgb(i, j, defaultColor);
			}
		}

		for (Node posNode : imageNode.getChildren()) {
			ImageArtifactData posArtifact = (ImageArtifactData) posNode.getArtifact().getData();
			int x = posArtifact.getValues()[0];
			int y = posArtifact.getValues()[1];

			int color = 0;
			if (posNode.getChildren().size() <= 0) {
				//System.out.println("There is no color for pos (" + x + ", " + y + ").");
				// color = 0;
				color = defaultColor;
			} else {
				if (posNode.getChildren().size() > 1) {
					//System.out.println("There is more than one color for pos (" + x + ", " + y + "). Picking the first one.");
				}

				int alpha = 0;
				int red = 0;
				int green = 0;
				int blue = 0;

				int relevantChildren = 0;
				for (Node colorNode : posNode.getChildren()) {
					ImageArtifactData colorArtifact = (ImageArtifactData) colorNode.getArtifact().getData();
					int pixelColor = (colorArtifact.getValues()[3] & 0x000000ff) | ((colorArtifact.getValues()[2] << 8) & 0x0000ff00) | ((colorArtifact.getValues()[1] << 16) & 0x00ff0000) | ((colorArtifact.getValues()[0] << 24) & 0xff000000);

					// TODO: store the actual background color of the image as image metadata nodes in the artifact tree and make use of it here!
					if (backgroundColor != pixelColor) {
						relevantChildren++;

						alpha += colorArtifact.getValues()[0];
						red += colorArtifact.getValues()[1];
						green += colorArtifact.getValues()[2];
						blue += colorArtifact.getValues()[3];
					}

					if (!enableBlending)
						break;
				}

				if (enableBlending && relevantChildren > 0) {
					alpha = alpha / relevantChildren;
					red = red / relevantChildren;
					green = green / relevantChildren;
					blue = blue / relevantChildren;
				}

				color = 0;
				color = (blue & 0x000000ff) | ((green << 8) & 0x0000ff00) | ((red << 16) & 0x00ff0000) | ((alpha << 24) & 0xff000000);
			}
			//outputImage.setRGB(x, y, color);
			pixelWriter.setArgb(x, y, color);
		}

		return outputImage;
	}

}
