package at.jku.isse.ecco.adapter.image;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Read -> write -> re-read of image files in each format the reader claims. A lossless format must
 * give back every pixel (as ARGB) the reader saw; JPEG is lossy and is held to ImageFormatWriteTest's
 * bound (every channel within 16).
 */
public class ImageRoundTripTest {

    private record RoundTrip(BufferedImage original, BufferedImage checkedOut, Path written) {
    }

    /** Writes {@code source} with ImageIO as a.{@code format}, reads it with the adapter, writes it back, and reads both. */
    private static RoundTrip roundTrip(BufferedImage source, String format) throws IOException {
        Path in = Files.createTempDirectory("image-round-trip");
        String name = "a." + format;
        assertTrue(ImageIO.write(source, format.equals("jpeg") ? "jpg" : format, in.resolve(name).toFile()), "no ImageIO writer for the fixture");
        return roundTripFile(in, name);
    }

    private static RoundTrip roundTripFile(Path in, String name) throws IOException {
        BufferedImage original = ImageIO.read(in.resolve(name).toFile());
        Set<Node.Op> nodes = new ImageReader(new SerEntityFactory()).read(in, new Path[]{Path.of(name)});
        Path out = Files.createTempDirectory("image-round-trip-out");
        Path[] written = new ImageFileWriter().write(out, Set.copyOf(nodes));
        assertArrayEquals(new Path[]{out.resolve(name)}, written);
        BufferedImage checkedOut = ImageIO.read(written[0].toFile());
        assertNotNull(checkedOut, "unreadable file written");
        assertEquals(original.getWidth(), checkedOut.getWidth());
        assertEquals(original.getHeight(), checkedOut.getHeight());
        return new RoundTrip(original, checkedOut, written[0]);
    }

    private static void assertSamePixels(BufferedImage expected, BufferedImage actual) {
        for (int y = 0; y < expected.getHeight(); y++)
            for (int x = 0; x < expected.getWidth(); x++)
                assertEquals(Integer.toHexString(expected.getRGB(x, y)), Integer.toHexString(actual.getRGB(x, y)), "pixel (" + x + "," + y + ")");
    }

    private static BufferedImage gradient(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                image.setRGB(x, y, 0xff000000 | (x * 37 % 256) << 16 | (y * 53 % 256) << 8 | (x * y * 11 % 256));
        return image;
    }

    // --- lossless formats ---

    @Test
    public void pngWithAlphaKeepsEveryPixelIncludingPartlyTransparentOnes() throws IOException {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffff0000);
        image.setRGB(1, 0, 0x80ff0000); // half transparent red
        image.setRGB(2, 0, 0x01020304); // almost transparent
        image.setRGB(0, 1, 0x00123456); // transparent, but with a color
        image.setRGB(1, 1, 0xfe00ff00);
        image.setRGB(2, 1, 0x00000000);
        RoundTrip rt = roundTrip(image, "png");
        assertSamePixels(rt.original(), rt.checkedOut());
        assertSamePixels(image, rt.checkedOut());
    }

    @Test
    public void opaquePngKeepsEveryPixel() throws IOException {
        RoundTrip rt = roundTrip(gradient(7, 3, BufferedImage.TYPE_INT_RGB), "png");
        assertSamePixels(rt.original(), rt.checkedOut());
    }

    @Test
    public void onePixelPng() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x7f102030);
        RoundTrip rt = roundTrip(image, "png");
        assertEquals(0x7f102030, rt.checkedOut().getRGB(0, 0));
    }

    @Test
    public void tallOneColumnPng() throws IOException {
        RoundTrip rt = roundTrip(gradient(1, 9, BufferedImage.TYPE_INT_RGB), "png");
        assertSamePixels(rt.original(), rt.checkedOut());
    }

    @Test
    public void indexedPngWithATransparentPaletteEntryKeepsEveryPixel() throws IOException {
        byte[] r = {0, (byte) 255, 0, 10}, g = {0, 0, (byte) 255, 20}, b = {0, 0, 0, 30};
        BufferedImage image = new BufferedImage(4, 1, BufferedImage.TYPE_BYTE_INDEXED, new IndexColorModel(8, 4, r, g, b, 3));
        for (int x = 0; x < 4; x++)
            image.getRaster().setSample(x, 0, 0, x);
        RoundTrip rt = roundTrip(image, "png");
        assertEquals(BufferedImage.TYPE_BYTE_INDEXED, rt.original().getType());
        assertSamePixels(rt.original(), rt.checkedOut());
        assertEquals(0, rt.checkedOut().getRGB(3, 0) >>> 24, "the transparent entry stays transparent");
    }

    @Test
    public void blackAndWhitePngKeepsEveryPixel() throws IOException {
        BufferedImage image = new BufferedImage(8, 2, BufferedImage.TYPE_BYTE_BINARY);
        image.setRGB(1, 0, 0xffffffff);
        image.setRGB(6, 1, 0xffffffff);
        RoundTrip rt = roundTrip(image, "png");
        assertSamePixels(rt.original(), rt.checkedOut());
    }

    @Test
    public void bmpKeepsEveryPixelOfANonSquareImage() throws IOException {
        RoundTrip rt = roundTrip(gradient(5, 2, BufferedImage.TYPE_INT_RGB), "bmp");
        assertSamePixels(rt.original(), rt.checkedOut());
    }

    @Test
    public void onePixelBmp() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xffffff);
        RoundTrip rt = roundTrip(image, "bmp");
        assertEquals(0xffffffff, rt.checkedOut().getRGB(0, 0));
    }

    @Test
    public void sixteenBitBmpKeepsEveryPixel() throws IOException {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_USHORT_555_RGB);
        image.setRGB(0, 0, 0xff0000);
        image.setRGB(1, 1, 0x00ff00);
        image.setRGB(2, 0, 0x0000ff);
        RoundTrip rt = roundTrip(image, "bmp");
        assertSamePixels(rt.original(), rt.checkedOut());
    }

    @Test
    public void monochromeBmpKeepsEveryPixel() throws IOException {
        BufferedImage image = new BufferedImage(8, 1, BufferedImage.TYPE_BYTE_BINARY);
        image.setRGB(3, 0, 0xffffffff);
        RoundTrip rt = roundTrip(image, "bmp");
        assertSamePixels(rt.original(), rt.checkedOut());
    }

    @Test
    public void opaqueGifKeepsEveryPixel() throws IOException {
        // height 5 or more: see interlacedGifTwoToFourRowsHighIsReadCorrectly
        RoundTrip rt = roundTrip(gradient(6, 5, BufferedImage.TYPE_INT_RGB), "gif");
        assertSamePixels(rt.original(), rt.checkedOut());
    }

    @Test
    public void onePixelGif() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        RoundTrip rt = roundTrip(image, "gif");
        assertEquals(0xff336699, rt.checkedOut().getRGB(0, 0));
    }

    @Test
    public void gifTransparencyStaysTransparent() throws IOException {
        byte[] r = {0, (byte) 255, 0, 10}, g = {0, 0, (byte) 255, 20}, b = {0, 0, 0, 30};
        BufferedImage image = new BufferedImage(4, 6, BufferedImage.TYPE_BYTE_INDEXED, new IndexColorModel(8, 4, r, g, b, 3));
        for (int x = 0; x < 4; x++)
            for (int y = 0; y < 6; y++)
                image.getRaster().setSample(x, y, 0, x);
        RoundTrip rt = roundTrip(image, "gif");
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 3; x++)
                assertEquals(rt.original().getRGB(x, y), rt.checkedOut().getRGB(x, y));
            assertEquals(0, rt.checkedOut().getRGB(3, y) >>> 24, "the transparent pixel stays transparent");
        }
    }

    // --- JPEG: lossy, held to the bound ImageFormatWriteTest uses ---

    private static int maxChannelDifference(BufferedImage a, BufferedImage b) {
        int max = 0;
        for (int x = 0; x < a.getWidth(); x++)
            for (int y = 0; y < a.getHeight(); y++)
                for (int shift = 0; shift <= 16; shift += 8)
                    max = Math.max(max, Math.abs((a.getRGB(x, y) >> shift & 0xff) - (b.getRGB(x, y) >> shift & 0xff)));
        return max;
    }

    @Test
    public void jpegWithTheLongExtensionIsWrittenAsJpeg() throws IOException {
        RoundTrip rt = roundTrip(gradient(9, 4, BufferedImage.TYPE_INT_RGB), "jpeg");
        byte[] bytes = Files.readAllBytes(rt.written());
        assertEquals((byte) 0xff, bytes[0]);
        assertEquals((byte) 0xd8, bytes[1], "JPEG start-of-image marker");
        assertTrue(maxChannelDifference(rt.original(), rt.checkedOut()) <= 16);
    }

    @Test
    public void onePixelJpeg() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x80c040);
        RoundTrip rt = roundTrip(image, "jpg");
        assertTrue(maxChannelDifference(rt.original(), rt.checkedOut()) <= 16);
    }

    // --- known losses, documented ---

    /**
     * A grayscale PNG keeps its gray levels: Java takes TYPE_BYTE_GRAY to be linear and getRGB()
     * brightened it to sRGB, so a pixel of 40 was checked out as (110, 110, 110).
     */
    @Test
    public void grayscalePngKeepsItsGrayLevels() throws IOException {
        BufferedImage image = new BufferedImage(3, 1, BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setSample(0, 0, 0, 0);
        image.getRaster().setSample(1, 0, 0, 40);
        image.getRaster().setSample(2, 0, 0, 255);
        RoundTrip rt = roundTrip(image, "png");
        for (int x = 0; x < 3; x++) {
            int expected = image.getRaster().getSample(x, 0, 0);
            for (int band = 0; band < 3; band++)
                assertEquals(expected, rt.checkedOut().getRaster().getSample(x, 0, band), "x " + x + ", band " + band);
            assertEquals(0xff, rt.checkedOut().getRaster().getSample(x, 0, 3), "opaque");
        }
    }

    /** Gray with alpha keeps both. */
    @Test
    public void grayscalePngWithAlphaKeepsItsGrayLevelsAndAlpha() throws IOException {
        java.awt.color.ColorSpace gray = java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_GRAY);
        java.awt.image.ComponentColorModel model = new java.awt.image.ComponentColorModel(gray, true, false,
                java.awt.Transparency.TRANSLUCENT, java.awt.image.DataBuffer.TYPE_BYTE);
        BufferedImage image = new BufferedImage(model, model.createCompatibleWritableRaster(1, 1), false, null);
        image.getRaster().setSample(0, 0, 0, 40);
        image.getRaster().setSample(0, 0, 1, 200);
        RoundTrip rt = roundTrip(image, "png");
        assertEquals(40, rt.checkedOut().getRaster().getSample(0, 0, 0));
        assertEquals(200, rt.checkedOut().getRaster().getSample(0, 0, 3));
    }

    /** Pixels are stored as 8-bit ARGB, so a 16-bit PNG loses its low bits on checkout. */
    @Test
    public void sixteenBitPngIsCheckedOutWithEightBitsCurrentBehaviour() throws IOException {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_USHORT_GRAY);
        image.getRaster().setSample(0, 0, 0, 1000);
        image.getRaster().setSample(1, 0, 0, 1001);
        RoundTrip rt = roundTrip(image, "png");
        assertEquals(16, rt.original().getColorModel().getComponentSize(0));
        assertEquals(8, rt.checkedOut().getColorModel().getComponentSize(0));
        assertEquals(rt.checkedOut().getRGB(0, 0), rt.checkedOut().getRGB(1, 0), "two 16-bit levels collapse into one");
    }

    /**
     * A pixel whose color equals the writer's background (0x00ffffff, transparent white) does not
     * count (ImageUtil.java:67), so with no other color it is written as 0 (ImageUtil.java:87):
     * transparent white becomes transparent black. Invisible, but the pixel data changes.
     */
    @Test
    public void transparentWhiteIsCheckedOutAsTransparentBlackCurrentBehaviour() throws IOException {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x00ffffff);
        image.setRGB(1, 0, 0xffffffff);
        RoundTrip rt = roundTrip(image, "png");
        assertEquals(0x00ffffff, rt.original().getRGB(0, 0));
        assertEquals(0x00000000, rt.checkedOut().getRGB(0, 0));
        assertEquals(0xffffffff, rt.checkedOut().getRGB(1, 0));
    }

    /** A 32-bit BMP: ImageIO reads it as TYPE_INT_RGB (1), and it is OPAQUE (1). */
    private static Path thirtyTwoBitBmp() throws IOException {
        int width = 2, height = 1;
        int dataSize = width * height * 4, size = 54 + dataSize;
        ByteBuffer bmp = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        bmp.put((byte) 'B').put((byte) 'M').putInt(size).putInt(0).putInt(54);
        bmp.putInt(40).putInt(width).putInt(height).putShort((short) 1).putShort((short) 32).putInt(0).putInt(dataSize).putInt(2835).putInt(2835).putInt(0).putInt(0);
        bmp.putInt(0x00ff0000).putInt(0x0000ff00);
        Path dir = Files.createTempDirectory("bmp32");
        Files.write(dir.resolve("a.bmp"), bmp.array());
        BufferedImage image = ImageIO.read(dir.resolve("a.bmp").toFile());
        assertEquals(BufferedImage.TYPE_INT_RGB, image.getType());
        assertEquals(BufferedImage.OPAQUE, image.getTransparency());
        return dir;
    }

    /**
     * Bug: the TYPE child [1] and the TRANSPARENCY child [1] are equal (ImageArtifactData.equals
     * ignores the type), so adding the second to the unordered plugin node throws (ImageReader.java:76).
     * Any image of TYPE_INT_RGB that is opaque - e.g. every 32-bit BMP - cannot be committed.
     */
    @Disabled("bug: 32-bit BMPs cannot be read: TYPE [1] equals TRANSPARENCY [1], ImageReader.java:75-76 / ImageArtifactData.java:42")
    @Test
    public void thirtyTwoBitBmpCanBeRead() throws IOException {
        Path dir = thirtyTwoBitBmp();
        RoundTrip rt = roundTripFile(dir, "a.bmp");
        assertSamePixels(rt.original(), rt.checkedOut());
    }

    @Test
    public void thirtyTwoBitBmpFailsToReadCurrentBehaviour() throws IOException {
        Path dir = thirtyTwoBitBmp();
        EccoException e = assertThrows(EccoException.class, () -> new ImageReader(new SerEntityFactory()).read(dir, new Path[]{Path.of("a.bmp")}));
        assertTrue(e.getMessage().contains("equivalent child"), e.getMessage());
    }

    /**
     * Bug in the JDK's GIF reader: an interlaced GIF 2 to 4 rows high (and ImageIO writes GIFs
     * interlaced) is decoded with its rows shifted - macOS's sips decodes the same file correctly.
     * ImageReader commits those wrong pixels (ImageReader.java:70).
     */
    @Disabled("bug: interlaced GIFs 2-4 rows high are read with wrong rows (JDK GIF reader), ImageReader.java:70")
    @Test
    public void interlacedGifTwoToFourRowsHighIsReadCorrectly() throws IOException {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 4; x++)
                image.setRGB(x, y, (y * 80) << 16 | x * 40);
        RoundTrip rt = roundTrip(image, "gif");
        assertSamePixels(image, rt.original());
        assertSamePixels(image, rt.checkedOut());
    }

    /** A truncated JPEG is read without an error: the missing part is committed as whatever the decoder filled in. */
    @Test
    public void truncatedJpegIsReadWithoutErrorCurrentBehaviour() throws IOException {
        Path dir = Files.createTempDirectory("truncated-jpeg");
        ImageIO.write(gradient(50, 50, BufferedImage.TYPE_INT_RGB), "jpg", dir.resolve("full.jpg").toFile());
        byte[] full = Files.readAllBytes(dir.resolve("full.jpg"));
        Files.write(dir.resolve("a.jpg"), Arrays.copyOf(full, full.length / 2));
        Set<Node.Op> nodes = new ImageReader(new SerEntityFactory()).read(dir, new Path[]{Path.of("a.jpg")});
        assertEquals(1, nodes.size());
    }

    // --- unreadable input ---

    @Test
    public void truncatedPngIsRefused() throws IOException {
        Path dir = Files.createTempDirectory("truncated-png");
        ImageIO.write(gradient(50, 50, BufferedImage.TYPE_INT_RGB), "png", dir.resolve("full.png").toFile());
        byte[] full = Files.readAllBytes(dir.resolve("full.png"));
        Files.write(dir.resolve("a.png"), Arrays.copyOf(full, full.length / 2));
        assertThrows(EccoException.class, () -> new ImageReader(new SerEntityFactory()).read(dir, new Path[]{Path.of("a.png")}));
    }

    @Test
    public void emptyFileIsRefused() throws IOException {
        Path dir = Files.createTempDirectory("empty-image");
        Files.write(dir.resolve("a.png"), new byte[0]);
        EccoException e = assertThrows(EccoException.class, () -> new ImageReader(new SerEntityFactory()).read(dir, new Path[]{Path.of("a.png")}));
        assertTrue(e.getMessage().startsWith("Could not read image"), e.getMessage());
    }

    @Test
    public void missingFileIsRefusedWithTheIoErrorAsCause() throws IOException {
        Path dir = Files.createTempDirectory("missing-image");
        EccoException e = assertThrows(EccoException.class, () -> new ImageReader(new SerEntityFactory()).read(dir, new Path[]{Path.of("missing.png")}));
        assertInstanceOf(IIOException.class, e.getCause());
    }

    @Test
    public void readWithoutABaseResolvesAgainstTheWorkingDirectory() throws IOException {
        Path dir = Files.createTempDirectory("image-no-base");
        ImageIO.write(gradient(2, 2, BufferedImage.TYPE_INT_RGB), "png", dir.resolve("a.png").toFile());
        // an absolute path resolves to itself against "."
        Set<Node.Op> nodes = new ImageReader(new SerEntityFactory()).read(new Path[]{dir.resolve("a.png")});
        assertEquals(1, nodes.size());
        ImageArtifactData size = (ImageArtifactData) nodes.iterator().next().getChildren().get(0).getArtifact().getData();
        assertArrayEquals(new int[]{2, 2}, size.getValues());
    }

    @Test
    public void readerListenersCanBeAddedAndRemoved() {
        ImageReader reader = new ImageReader(new SerEntityFactory());
        at.jku.isse.ecco.service.listener.ReadListener listener = new at.jku.isse.ecco.service.listener.ReadListener() {
        };
        reader.addListener(listener);
        reader.removeListener(listener);
    }
}
