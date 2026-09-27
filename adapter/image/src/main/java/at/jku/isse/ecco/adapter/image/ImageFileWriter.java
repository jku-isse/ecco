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

				String fileName = outputPath.getFileName().toString();
				String fileType = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
				try {
					writeImage(outputImage, fileType, outputPath);
				} catch (IOException e) {
					throw new EccoException("Could not write image " + outputPath, e);
				}
			}
		}

		return output.toArray(new Path[output.size()]);
	}

	/**
	 * Writes {@code image} (which has an alpha channel) in the file's format. JPEG and BMP have no
	 * alpha channel, and ImageIO's encoders for them write nothing for an image with one - they
	 * return false, and every checked-out .jpg and .bmp used to be an empty file. They get the image
	 * without its alpha channel (the writer already blended transparent pixels onto the background),
	 * JPEG at the highest quality, since every checkout encodes it again.
	 */
	private static void writeImage(BufferedImage image, String fileType, Path outputPath) throws IOException {
		BufferedImage toWrite = image;
		if (fileType.equals("jpg") || fileType.equals("jpeg") || fileType.equals("bmp")) {
			toWrite = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D graphics = toWrite.createGraphics();
			graphics.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
			graphics.dispose();
		}
		if (fileType.equals("jpg") || fileType.equals("jpeg")) {
			javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
			javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(1.0f);
			try (javax.imageio.stream.ImageOutputStream out = ImageIO.createImageOutputStream(Files.newOutputStream(outputPath))) {
				writer.setOutput(out);
				writer.write(null, new javax.imageio.IIOImage(toWrite, null, null), param);
			} finally {
				writer.dispose();
			}
			return;
		}
		if (!ImageIO.write(toWrite, fileType, outputPath.toFile()))
			throw new EccoException("No image writer for the format of " + outputPath);
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
