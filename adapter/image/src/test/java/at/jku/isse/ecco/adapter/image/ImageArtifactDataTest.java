package at.jku.isse.ecco.adapter.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ImageArtifactDataTest {

    private static ImageArtifactData color(int a, int r, int g, int b) {
        return new ImageArtifactData(new int[]{a, r, g, b}, ImageReader.TYPE_COLOR);
    }

    @Test
    public void equalValuesAreEqualWithTheSameHash() {
        ImageArtifactData one = color(255, 1, 2, 3);
        ImageArtifactData other = color(255, 1, 2, 3);
        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());
        assertEquals(one, one);
    }

    @Test
    public void colorsThatDifferOnlyInAlphaAreNotEqual() {
        assertNotEquals(color(255, 1, 2, 3), color(254, 1, 2, 3));
        assertNotEquals(color(0, 1, 2, 3), color(255, 1, 2, 3));
    }

    @Test
    public void eachChannelCounts() {
        assertNotEquals(color(255, 1, 2, 3), color(255, 0, 2, 3));
        assertNotEquals(color(255, 1, 2, 3), color(255, 1, 0, 3));
        assertNotEquals(color(255, 1, 2, 3), color(255, 1, 2, 0));
    }

    @Test
    public void positionsAreDistinguishedByOrder() {
        assertNotEquals(new ImageArtifactData(new int[]{1, 2}, ImageReader.TYPE_POS), new ImageArtifactData(new int[]{2, 1}, ImageReader.TYPE_POS));
    }

    @Test
    public void nullAndOtherClassesAreNotEqual() {
        assertFalse(color(255, 1, 2, 3).equals(null));
        assertNotEquals("[255, 1, 2, 3]", color(255, 1, 2, 3));
        assertNotEquals(new ImageArtifactData() {
        }, new ImageArtifactData(null, null), "an anonymous subclass is another class");
    }

    @Test
    public void noValuesEqualsNoValues() {
        assertEquals(new ImageArtifactData(null, "x"), new ImageArtifactData(null, "y"));
        assertEquals(new ImageArtifactData(null, "x").hashCode(), new ImageArtifactData(null, "y").hashCode());
    }

    /**
     * The type is not part of equality: the TYPE child [1] equals the TRANSPARENCY child [1], which
     * is why 32-bit BMPs cannot be read (ImageRoundTripTest.thirtyTwoBitBmpCanBeRead).
     */
    @Test
    public void theTypeIsIgnoredCurrentBehaviour() {
        assertEquals(new ImageArtifactData(new int[]{1}, "TYPE"), new ImageArtifactData(new int[]{1}, "TRANSPARENCY"));
        assertEquals(new ImageArtifactData(new int[]{3, 4}, ImageReader.TYPE_IMAGE), new ImageArtifactData(new int[]{3, 4}, ImageReader.TYPE_POS));
    }

    @Test
    public void toStringShowsTheValuesAndGettersReturnThem() {
        ImageArtifactData data = color(255, 1, 2, 3);
        assertEquals("[255, 1, 2, 3]", data.toString());
        assertEquals(ImageReader.TYPE_COLOR, data.getType());
        assertArrayEquals(new int[]{255, 1, 2, 3}, data.getValues());
    }
}
