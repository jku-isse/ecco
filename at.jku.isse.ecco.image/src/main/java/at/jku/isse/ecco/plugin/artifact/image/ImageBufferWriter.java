package at.jku.isse.ecco.plugin.artifact.image;

import at.jku.isse.ecco.listener.WriteListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ImageBufferWriter implements ArtifactWriter<Set<Node>, BufferedImage> {

	private int backgroundColor = 0x00ffffff;
	private boolean enableBlending = true;

	@Override
	public String getPluginId() {
		return ImagePlugin.class.getName();
	}

	@Override
	public BufferedImage[] write(Set<Node> nodes) {
		return this.write(null, nodes);
	}

	@Override
	public BufferedImage[] write(BufferedImage base, Set<Node> nodes) {
		List<BufferedImage> output = new ArrayList<BufferedImage>();

		for (Node fileNode : nodes) {
			if (!(fileNode.getArtifact().getData() instanceof PluginArtifactData)) {
				System.out.println("Top nodes must be plugin nodes!");
			} else {
				PluginArtifactData fileArtifact = (PluginArtifactData) fileNode.getArtifact().getData();

				if (fileNode.getChildren().size() != 1 || !(((Node) fileNode.getChildren().iterator().next()).getArtifact().getData() instanceof ImageArtifactData)
						|| !((ImageArtifactData) ((Node) fileNode.getChildren().iterator().next()).getArtifact().getData()).getType().equals("IMAGE")) {
					System.out.println("There must be exactly one image node!");
				} else {
					Node imageNode = (Node) fileNode.getChildren().iterator().next();
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

						defaultColor = this.backgroundColor;
					}

					BufferedImage outputImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
					// TODO: include image metadata in artifacts, like type or color model

					for (int i = 0; i < width; i++) {
						for (int j = 0; j < height; j++) {
							outputImage.setRGB(i, j, defaultColor);
						}
					}

					for (Node posNode : imageNode.getChildren()) {
						ImageArtifactData posArtifact = (ImageArtifactData) posNode.getArtifact().getData();
						int x = posArtifact.getValues()[0];
						int y = posArtifact.getValues()[1];

						int color = 0;
						if (posNode.getChildren().size() <= 0) {
							System.out.println("There is no color for pos (" + x + ", " + y + ").");
							// color = 0;
							color = defaultColor;
						} else {
							if (posNode.getChildren().size() > 1) {
								System.out.println("There is more than one color for pos (" + x + ", " + y + "). Picking the first one.");
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
								if (this.backgroundColor != pixelColor) {
									relevantChildren++;

									alpha += colorArtifact.getValues()[0];
									red += colorArtifact.getValues()[1];
									green += colorArtifact.getValues()[2];
									blue += colorArtifact.getValues()[3];
								}

								if (!this.enableBlending)
									break;
							}

							if (this.enableBlending && relevantChildren > 0) {
								alpha = alpha / relevantChildren;
								red = red / relevantChildren;
								green = green / relevantChildren;
								blue = blue / relevantChildren;
							}

							color = 0;
							color = (blue & 0x000000ff) | ((green << 8) & 0x0000ff00) | ((red << 16) & 0x00ff0000) | ((alpha << 24) & 0xff000000);
						}
						outputImage.setRGB(x, y, color);
					}

					output.add(outputImage);
				}

			}
		}

		return output.toArray(new BufferedImage[output.size()]);
	}


	private Collection<WriteListener> listeners = new ArrayList<WriteListener>();

	@Override
	public void addListener(WriteListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(WriteListener listener) {
		this.listeners.remove(listener);
	}

}
