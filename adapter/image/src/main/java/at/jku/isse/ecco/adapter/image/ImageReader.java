package at.jku.isse.ecco.adapter.image;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class ImageReader implements ArtifactReader<Path, Set<Node.Op>> {

	public static final String TYPE_IMAGE = "IMAGE";
	public static final String TYPE_POS = "POS";
	public static final String TYPE_COLOR = "COLOR";

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

	private static Map<Integer, String[]> prioritizedPatterns;

	static {
		prioritizedPatterns = new HashMap<>();
		prioritizedPatterns.put(1, new String[]{"**.png", "**.jpg", "**.bmp", "**.gif", "**.jpeg"});
	}

	@Override
	public Map<Integer, String[]> getPrioritizedPatterns() {
		return Collections.unmodifiableMap(prioritizedPatterns);
	}

	@Override
	public Set<Node.Op> read(Path[] input) {
		return this.read(Paths.get("."), input);
	}

	@Override
	public Set<Node.Op> read(Path base, Path[] input) {
		final Set<Node.Op> nodes = new LinkedHashSet<>();

		for (Path path : input) {
			Path resolvedPath = base.resolve(path);

			try {
				Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
				Node.Op pluginNode = this.entityFactory.createNode(pluginArtifact);
				nodes.add(pluginNode);

				final BufferedImage image = ImageIO.read(resolvedPath.toFile());
				if (image == null)
					throw new EccoException("Could not read image: " + resolvedPath);
				pluginNode.addChild(parseImage(image));

				pluginNode.addChild(this.entityFactory.createNode(new ImageArtifactData(new int[]{image.getType()}, "TYPE")));
				pluginNode.addChild(this.entityFactory.createNode(new ImageArtifactData(new int[]{image.getTransparency()}, "TRANSPARENCY")));

				// TODO: also store other image properties like global background color and metadata!
				System.out.println(image.getColorModel());
			} catch (IOException e) {
				throw new EccoException("Could not read the image: " + resolvedPath, e);
			}
		}

		return nodes;
	}

	private Node.Op parseImage(final BufferedImage image) {
		final ImageArtifactData imageArtifactData = new ImageArtifactData(new int[]{image.getWidth(), image.getHeight()}, TYPE_IMAGE);

		final Node.Op imageNode = this.entityFactory.createNode(this.entityFactory.createArtifact(imageArtifactData));

		List<Node.Op> pixelNode = parsePixelData(image);

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

	private List<Node.Op> parsePixelData(final BufferedImage image) {
		assert image != null;

		List<Node.Op> nodes = new ArrayList<>();
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				final int[] position = new int[]{x, y};
				final ImageArtifactData posArtifactData = new ImageArtifactData(position, TYPE_POS);

				final int[] rgb = this.getPixel(image, x, y);
				final ImageArtifactData colorArtifactData = new ImageArtifactData(rgb, TYPE_COLOR);

				final Node.Op positionNode = this.entityFactory.createNode(this.entityFactory.createArtifact(posArtifactData));

				final Node.Op colorNode = this.entityFactory.createNode(this.entityFactory.createArtifact(colorArtifactData));

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
