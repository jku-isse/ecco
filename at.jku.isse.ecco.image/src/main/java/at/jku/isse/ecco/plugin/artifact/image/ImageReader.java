package at.jku.isse.ecco.plugin.artifact.image;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.listener.ReadListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class ImageReader implements ArtifactReader<Path, Set<Node>> {

	private static final Logger LOGGER = LoggerFactory.getLogger(ImageReader.class);

	private final EntityFactory entityFactory;

	@Inject
	public ImageReader(final EntityFactory entityFactory) {
		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public String getPluginId() {
		return ImagePlugin.class.getName();
	}

	private static final String[] typeHierarchy = new String[]{"image"};

	@Override
	public String[] getTypeHierarchy() {
		return typeHierarchy;
	}

	@Override
	public boolean canRead(Path path) {
		// TODO: actually check if file is an image
		String lowerCaseFileName = path.getFileName().toString().toLowerCase();
		if (!Files.isDirectory(path) && Files.isRegularFile(path) && lowerCaseFileName.endsWith(".jpg") || lowerCaseFileName.endsWith(".jpeg") || lowerCaseFileName.endsWith(".gif") || lowerCaseFileName.endsWith(".png"))
			return true;
		else
			return false;
	}

	@Override
	public Set<Node> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node> read(Path base, Path[] input) {
		final Set<Node> nodes = new LinkedHashSet<>();

		for (Path path : input) {
			Path resolvedPath = base.resolve(path);

			try {
				Artifact<PluginArtifactData> fileArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
				Node fileNode = this.entityFactory.createOrderedNode(fileArtifact);
				nodes.add(fileNode);

				final BufferedImage image = ImageIO.read(resolvedPath.toFile());
				// nodes.add(parseImage(image));
				fileNode.addChild(parseImage(image));

				System.out.println(image.getColorModel());
				System.out.println(image.getType());
			} catch (IOException e) {
				LOGGER.error("Could not read the image: " + resolvedPath, e);
			}
		}

		return nodes;
	}

	private Node parseImage(final BufferedImage image) {
		final ImageArtifactData imageArtifactData = new ImageArtifactData(new int[]{image.getWidth(), image.getHeight()}, "IMAGE");

		final Node imageNode = this.entityFactory.createNode(this.entityFactory.createArtifact(imageArtifactData));

		List<Node> pixelNode = parsePixelData(image);

		pixelNode.forEach(imageNode::addChild);

		return imageNode;
	}

	private int[] getPixel(BufferedImage image, int x, int y) {
		assert image != null;
		assert x >= 0 && x < image.getWidth() : "Expected x to be in image range.";
		assert y >= 0 && y < image.getHeight() : "Expected y to be in image range.";

		int argb = image.getRGB(x, y);

		return new int[]{(argb >> 24) & 0xff, // alpha
				(argb >> 16) & 0xff, // red
				(argb >> 8) & 0xff, // green
				argb & 0xff // blue
		};
	}

	private List<Node> parsePixelData(final BufferedImage image) {
		assert image != null;

		List<Node> nodes = new ArrayList<>();
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				final int[] position = new int[]{x, y};
				final ImageArtifactData posArtifactData = new ImageArtifactData(position, "POS");

				final int[] rgb = this.getPixel(image, x, y);
				final ImageArtifactData colorArtifactData = new ImageArtifactData(rgb, "COLOR");

				final Node positionNode = this.entityFactory.createNode(this.entityFactory.createArtifact(posArtifactData));

				final Node colorNode = this.entityFactory.createNode(this.entityFactory.createArtifact(colorArtifactData));

				positionNode.addChild(colorNode);

				nodes.add(positionNode);
			}
		}

		return nodes;
	}


	private Collection<ReadListener> listeners = new ArrayList<ReadListener>();

	@Override
	public void addListener(ReadListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(ReadListener listener) {
		this.listeners.remove(listener);
	}

}
