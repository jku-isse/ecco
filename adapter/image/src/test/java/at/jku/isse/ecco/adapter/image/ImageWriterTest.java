package at.jku.isse.ecco.adapter.image;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How an image's artifact tree becomes pixels (ImageUtil, shared by ImageFileWriter and
 * AwtImageWriter): a position without a color gets the background, a position with several colors
 * (composed from several variants) gets their average, ignoring colors equal to the background.
 */
public class ImageWriterTest {

    private static final int BACKGROUND = 0x00ffffff;

    private final SerEntityFactory factory = new SerEntityFactory();

    /** A plugin node for {@code path} holding a {@code width} x {@code height} image without positions. */
    private Node.Op pluginNode(String path, int width, int height) {
        Node.Op plugin = factory.createNode(new PluginArtifactData(ImagePlugin.class.getName(), Path.of(path)));
        plugin.addChild(factory.createNode(new ImageArtifactData(new int[]{width, height}, ImageReader.TYPE_IMAGE)));
        return plugin;
    }

    private Node.Op position(Node.Op plugin, int x, int y, int[]... argbColors) {
        Node.Op pos = factory.createNode(new ImageArtifactData(new int[]{x, y}, ImageReader.TYPE_POS));
        for (int[] color : argbColors)
            pos.addChild(factory.createNode(new ImageArtifactData(color, ImageReader.TYPE_COLOR)));
        plugin.getChildren().get(0).addChild(pos);
        return pos;
    }

    private static BufferedImage render(Node plugin) {
        return ImageUtil.createBufferedImage(plugin, BACKGROUND, true);
    }

    @Test
    public void aPixelWithoutAnyPositionGetsTheBackground() {
        Node.Op plugin = pluginNode("a.png", 2, 1);
        position(plugin, 0, 0, new int[]{255, 1, 2, 3});
        BufferedImage image = render(plugin);
        assertEquals(0xff010203, image.getRGB(0, 0));
        assertEquals(BACKGROUND, image.getRGB(1, 0));
    }

    @Test
    public void aPositionWithoutAColorGetsTheBackground() {
        Node.Op plugin = pluginNode("a.png", 1, 1);
        position(plugin, 0, 0);
        assertEquals(BACKGROUND, render(plugin).getRGB(0, 0));
    }

    @Test
    public void aPositionWithTwoColorsGetsTheirAverage() {
        Node.Op plugin = pluginNode("a.png", 1, 1);
        position(plugin, 0, 0, new int[]{255, 200, 0, 100}, new int[]{255, 0, 100, 51});
        assertEquals(0xff64324b, render(plugin).getRGB(0, 0));
    }

    @Test
    public void aColorEqualToTheBackgroundIsLeftOutOfTheAverage() {
        Node.Op plugin = pluginNode("a.png", 1, 1);
        position(plugin, 0, 0, new int[]{0, 255, 255, 255}, new int[]{255, 10, 20, 30});
        assertEquals(0xff0a141e, render(plugin).getRGB(0, 0));
    }

    @Test
    public void aPositionWhoseOnlyColorIsTheBackgroundIsWrittenTransparentBlack() {
        // the background color is not written back: see ImageRoundTripTest.transparentWhiteIsCheckedOutAsTransparentBlackCurrentBehaviour
        Node.Op plugin = pluginNode("a.png", 1, 1);
        position(plugin, 0, 0, new int[]{0, 255, 255, 255});
        assertEquals(0, render(plugin).getRGB(0, 0));
    }

    @Test
    public void colorsThatDifferOnlyInAlphaAreBothKeptAndAveraged() {
        Node.Op plugin = pluginNode("a.png", 1, 1);
        position(plugin, 0, 0, new int[]{255, 10, 20, 30}, new int[]{55, 10, 20, 30});
        assertEquals(0x9b0a141e, render(plugin).getRGB(0, 0));
    }

    // --- ImageFileWriter ---

    @Test
    public void fileWriterRefusesANonPluginTopNode() {
        Node.Op notAPlugin = factory.createNode(new ImageArtifactData(new int[]{1, 1}, ImageReader.TYPE_IMAGE));
        assertThrows(EccoException.class, () -> new ImageFileWriter().write(Path.of("."), Set.of(notAPlugin)));
    }

    @Test
    public void fileWriterWithoutABaseWritesToTheResolvedPath() throws IOException {
        Path dir = Files.createTempDirectory("image-writer-no-base");
        Node.Op plugin = pluginNode(dir.resolve("a.png").toString(), 1, 1);
        position(plugin, 0, 0, new int[]{255, 1, 2, 3});
        Path[] written = new ImageFileWriter().write(Set.of(plugin));
        assertArrayEquals(new Path[]{dir.resolve("a.png")}, written);
        assertEquals(0xff010203, ImageIO.read(written[0].toFile()).getRGB(0, 0));
    }

    @Test
    public void fileWriterRefusesAFileTypeWithoutAnImageWriter() throws IOException {
        Path dir = Files.createTempDirectory("image-writer-unknown");
        Node.Op plugin = pluginNode("a.xyz", 1, 1);
        EccoException e = assertThrows(EccoException.class, () -> new ImageFileWriter().write(dir, Set.of(plugin)));
        assertTrue(e.getMessage().startsWith("No image writer"), e.getMessage());
    }

    @Test
    public void fileWriterReportsAnUnwritablePngTarget() throws IOException {
        Path dir = Files.createTempDirectory("image-writer-unwritable");
        Node.Op plugin = pluginNode("missing-dir/a.png", 1, 1);
        EccoException e = assertThrows(EccoException.class, () -> new ImageFileWriter().write(dir, Set.of(plugin)));
        assertInstanceOf(IOException.class, e.getCause());
        assertFalse(Files.exists(dir.resolve("missing-dir")));
    }

    @Test
    public void fileWriterReportsAnUnwritableJpegTarget() throws IOException {
        Path dir = Files.createTempDirectory("image-writer-unwritable-jpeg");
        Node.Op plugin = pluginNode("missing-dir/a.jpg", 1, 1);
        EccoException e = assertThrows(EccoException.class, () -> new ImageFileWriter().write(dir, Set.of(plugin)));
        assertInstanceOf(IOException.class, e.getCause());
    }

    @Test
    public void fileWriterUpperCaseExtensionIsWrittenInItsFormat() throws IOException {
        Path dir = Files.createTempDirectory("image-writer-upper");
        Node.Op plugin = pluginNode("a.JPG", 2, 1);
        position(plugin, 0, 0, new int[]{255, 200, 100, 50});
        position(plugin, 1, 0, new int[]{255, 200, 100, 50});
        new ImageFileWriter().write(dir, Set.of(plugin));
        byte[] bytes = Files.readAllBytes(dir.resolve("a.JPG"));
        assertTrue(bytes.length > 0);
        assertEquals((byte) 0xd8, bytes[1], "JPEG start-of-image marker");
    }

    @Test
    public void jpegAndBmpGetTransparentPixelsOnWhite() throws IOException {
        for (String format : new String[]{"jpg", "bmp"}) {
            Path dir = Files.createTempDirectory("image-writer-flatten");
            Node.Op plugin = pluginNode("a." + format, 1, 1);
            position(plugin, 0, 0, new int[]{0, 0, 0, 0});
            new ImageFileWriter().write(dir, Set.of(plugin));
            int rgb = ImageIO.read(dir.resolve("a." + format).toFile()).getRGB(0, 0);
            for (int shift = 0; shift <= 16; shift += 8)
                assertTrue((rgb >> shift & 0xff) >= 250, format + ": " + Integer.toHexString(rgb));
        }
    }

    @Test
    public void fileWriterListenersAndPluginId() {
        ImageFileWriter writer = new ImageFileWriter();
        WriteListener listener = new WriteListener() {
        };
        writer.addListener(listener);
        writer.removeListener(listener);
        assertEquals(ImagePlugin.class.getName(), writer.getPluginId());
    }

    // --- AwtImageWriter ---

    @Test
    public void awtWriterRendersOneImagePerPluginNode() {
        Node.Op plugin = pluginNode("a.png", 2, 3);
        position(plugin, 1, 2, new int[]{128, 1, 2, 3});
        AwtImageWriter writer = new AwtImageWriter();
        BufferedImage[] images = writer.write(Set.of(plugin));
        assertEquals(1, images.length);
        assertEquals(2, images[0].getWidth());
        assertEquals(3, images[0].getHeight());
        assertEquals(0x80010203, images[0].getRGB(1, 2));
        assertEquals(BACKGROUND, images[0].getRGB(0, 0));
    }

    @Test
    public void awtWriterRefusesANonPluginTopNode() {
        Node.Op notAPlugin = factory.createNode(new ImageArtifactData(new int[]{1, 1}, ImageReader.TYPE_IMAGE));
        assertThrows(EccoException.class, () -> new AwtImageWriter().write(null, Set.of(notAPlugin)));
    }

    @Test
    public void awtWriterListenersAndPluginId() {
        AwtImageWriter writer = new AwtImageWriter();
        WriteListener listener = new WriteListener() {
        };
        writer.addListener(listener);
        writer.removeListener(listener);
        assertEquals(ImagePlugin.class.getName(), writer.getPluginId());
    }
}
