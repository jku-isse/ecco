package at.jku.isse.ecco.adapter.image;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ImageReaderTest {

    private final ImageReader reader = new ImageReader(new SerEntityFactory());

    /** A 2x2 PNG with distinct, known ARGB pixels - PNG preserves alpha exactly, so this round-trips. */
    private Path writeTwoByTwoPng() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, argb(255, 255, 0, 0));   // opaque red
        image.setRGB(1, 0, argb(255, 0, 255, 0));   // opaque green
        image.setRGB(0, 1, argb(255, 0, 0, 255));   // opaque blue
        image.setRGB(1, 1, argb(0, 0, 0, 0));       // fully transparent

        Path baseDir = Files.createTempDirectory("image-reader");
        Path file = baseDir.resolve("a.png");
        ImageIO.write(image, "png", file.toFile());
        return baseDir;
    }

    private int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Test
    public void readProducesAPluginNodeWithImageTypeAndTransparencyChildren() throws IOException {
        Path baseDir = writeTwoByTwoPng();

        Node.Op pluginNode = reader.read(baseDir, new Path[]{Path.of("a.png")}).iterator().next();

        assertInstanceOf(PluginArtifactData.class, pluginNode.getArtifact().getData());
        List<? extends Node.Op> children = pluginNode.getChildren();
        assertEquals(3, children.size());

        ImageArtifactData imageData = (ImageArtifactData) children.get(0).getArtifact().getData();
        assertEquals(ImageReader.TYPE_IMAGE, imageData.getType());
        assertArrayEquals(new int[]{2, 2}, imageData.getValues());

        assertEquals("TYPE", ((ImageArtifactData) children.get(1).getArtifact().getData()).getType());
        assertEquals("TRANSPARENCY", ((ImageArtifactData) children.get(2).getArtifact().getData()).getType());
    }

    @Test
    public void readProducesOnePositionNodePerPixelWithTheExactRoundTrippedColor() throws IOException {
        Path baseDir = writeTwoByTwoPng();

        Node.Op pluginNode = reader.read(baseDir, new Path[]{Path.of("a.png")}).iterator().next();
        Node.Op imageNode = pluginNode.getChildren().get(0);

        List<? extends Node.Op> pixelNodes = imageNode.getChildren();
        assertEquals(4, pixelNodes.size(), "one position node per pixel in a 2x2 image");

        Node.Op redPixel = findPixelAt(pixelNodes, 0, 0);
        Node.Op colorNode = redPixel.getChildren().get(0);
        ImageArtifactData colorData = (ImageArtifactData) colorNode.getArtifact().getData();
        assertEquals(ImageReader.TYPE_COLOR, colorData.getType());
        assertArrayEquals(new int[]{255, 255, 0, 0}, colorData.getValues(), "alpha, red, green, blue for opaque red");

        Node.Op transparentPixel = findPixelAt(pixelNodes, 1, 1);
        ImageArtifactData transparentColorData = (ImageArtifactData) transparentPixel.getChildren().get(0).getArtifact().getData();
        assertEquals(0, transparentColorData.getValues()[0], "alpha channel of the fully transparent pixel");
    }

    private Node.Op findPixelAt(List<? extends Node.Op> pixelNodes, int x, int y) {
        for (Node.Op node : pixelNodes) {
            ImageArtifactData posData = (ImageArtifactData) node.getArtifact().getData();
            assertEquals(ImageReader.TYPE_POS, posData.getType());
            if (posData.getValues()[0] == x && posData.getValues()[1] == y) {
                return node;
            }
        }
        throw new AssertionError("no position node found for (" + x + "," + y + ")");
    }

    @Test
    public void readOfANonImageFileThrows() throws IOException {
        Path baseDir = Files.createTempDirectory("image-reader-invalid");
        Files.writeString(baseDir.resolve("not-an-image.png"), "this is not a PNG file");

        assertThrows(EccoException.class, () -> reader.read(baseDir, new Path[]{Path.of("not-an-image.png")}));
    }

    @Test
    public void getPluginIdIsTheImagePluginClassName() {
        assertEquals(ImagePlugin.class.getName(), reader.getPluginId());
    }

    @Test
    public void getPrioritizedPatternsIncludesCommonImageExtensions() {
        assertTrue(List.of(reader.getPrioritizedPatterns().get(1)).contains("**.png"));
    }
}
