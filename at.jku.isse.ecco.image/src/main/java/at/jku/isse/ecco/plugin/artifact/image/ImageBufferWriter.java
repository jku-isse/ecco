package at.jku.isse.ecco.plugin.artifact.image;

import at.jku.isse.ecco.EccoException;
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

		for (Node pluginNode : nodes) {
			if (!(pluginNode.getArtifact().getData() instanceof PluginArtifactData)) {
				throw new EccoException("Top nodes must be plugin nodes!");
			} else {
				if (pluginNode.getChildren().size() != 1 || !(((Node) pluginNode.getChildren().iterator().next()).getArtifact().getData() instanceof ImageArtifactData) || !((ImageArtifactData) ((Node) pluginNode.getChildren().iterator().next()).getArtifact().getData()).getType().equals("IMAGE")) {
					throw new EccoException("There must be exactly one image node!");
				} else {
					BufferedImage outputImage = ImageUtil.createBufferedImage(pluginNode, this.backgroundColor, this.enableBlending);
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
