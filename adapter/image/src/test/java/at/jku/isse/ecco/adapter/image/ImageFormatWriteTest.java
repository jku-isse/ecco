package at.jku.isse.ecco.adapter.image;

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
 * The writer built every image with an alpha channel, which ImageIO's JPEG and BMP encoders cannot
 * write: ImageIO.write() returned false, unchecked, and every checked-out .jpg/.bmp was an empty file.
 */
public class ImageFormatWriteTest {

    private static BufferedImage roundTrip(String format) throws IOException {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 40; x++)
            for (int y = 0; y < 30; y++)
                image.setRGB(x, y, (x * 6) << 16 | (y * 8) << 8 | 128);
        Path in = Files.createTempDirectory("image-format");
        ImageIO.write(image, format, in.resolve("a." + format).toFile());

        Set<Node.Op> nodes = new ImageReader(new SerEntityFactory()).read(in, new Path[]{Path.of("a." + format)});
        Path out = Files.createTempDirectory("image-format-out");
        new ImageFileWriter().write(out, Set.copyOf(nodes));

        Path written = out.resolve("a." + format);
        assertTrue(Files.size(written) > 0, "empty " + format + " file written");
        BufferedImage read = ImageIO.read(written.toFile());
        assertNotNull(read, "unreadable " + format + " file written");
        assertEquals(40, read.getWidth());
        assertEquals(30, read.getHeight());
        return read;
    }

    private static int maxChannelDifference(BufferedImage a, BufferedImage b) {
        int max = 0;
        for (int x = 0; x < a.getWidth(); x++)
            for (int y = 0; y < a.getHeight(); y++)
                for (int shift = 0; shift <= 16; shift += 8)
                    max = Math.max(max, Math.abs((a.getRGB(x, y) >> shift & 0xff) - (b.getRGB(x, y) >> shift & 0xff)));
        return max;
    }

    @Test
    public void bmpIsWrittenWithTheSamePixels() throws IOException {
        BufferedImage written = roundTrip("bmp");
        // lossless: every pixel as read
        for (int x = 0; x < 40; x++)
            for (int y = 0; y < 30; y++)
                assertEquals((x * 6) << 16 | (y * 8) << 8 | 128, written.getRGB(x, y) & 0xffffff);
    }

    @Test
    public void jpegIsWrittenAtHighQuality() throws IOException {
        BufferedImage written = roundTrip("jpg");
        BufferedImage expected = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 40; x++)
            for (int y = 0; y < 30; y++)
                expected.setRGB(x, y, (x * 6) << 16 | (y * 8) << 8 | 128);
        // JPEG is lossy (the original was a JPEG already); the checkout must not degrade it noticeably
        assertTrue(maxChannelDifference(expected, written) <= 16, "max channel difference " + maxChannelDifference(expected, written));
    }
}
