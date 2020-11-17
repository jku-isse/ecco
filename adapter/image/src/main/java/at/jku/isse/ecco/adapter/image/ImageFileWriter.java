package at.jku.isse.ecco.adapter.image;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ImageFileWriter implements ArtifactWriter<Set<Node>, Path> {

	private int backgroundColor = 0x00ffffff;
	private boolean enableBlending = true;

	@Override
	public String getPluginId() {
		return ImagePlugin.class.getName();
	}

	@Override
	public Path[] write(Set<Node> nodes) {
		return this.write(Paths.get("."), nodes);
	}

	@Override
	public Path[] write(Path base, Set<Node> nodes) {
		List<Path> output = new ArrayList<>();

		for (Node pluginNode : nodes) {
			if (!(pluginNode.getArtifact().getData() instanceof PluginArtifactData)) {
				throw new EccoException("Top nodes must be plugin nodes!");
			} else {
				PluginArtifactData pluginArtifactData = (PluginArtifactData) pluginNode.getArtifact().getData();

				Path outputPath = base.resolve(pluginArtifactData.getPath());
				output.add(outputPath);

				BufferedImage outputImage = ImageUtil.createBufferedImage(pluginNode, this.backgroundColor, this.enableBlending);

				try {
					if (!Files.exists(outputPath)) {
						Files.createFile(outputPath);
					}
					String fileName = outputPath.getFileName().toString();
					String fileType = fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length());
					ImageIO.write(outputImage, fileType, outputPath.toFile());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return output.toArray(new Path[output.size()]);
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

}
