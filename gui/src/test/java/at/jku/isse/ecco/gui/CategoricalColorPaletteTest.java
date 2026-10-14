package at.jku.isse.ecco.gui;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shared categorical palette: 16 distinct slots in a fixed order, cycling past the end (also
 * for negative indices), and a background tint that keeps the hue but moves towards white.
 */
public class CategoricalColorPaletteTest {

    /** 16 distinct colors, the first one blue, none of them the "other" grey. */
    @Test
    public void hasSixteenDistinctSlotsInAFixedOrder() {
        assertEquals(16, CategoricalColorPalette.size());
        Set<Color> colors = new HashSet<>();
        for (int i = 0; i < CategoricalColorPalette.size(); i++)
            colors.add(CategoricalColorPalette.colorAt(i));
        assertEquals(16, colors.size());
        assertFalse(colors.contains(CategoricalColorPalette.OTHER));
        assertEquals(Color.web("#2a78d6"), CategoricalColorPalette.colorAt(0));
        assertEquals(Color.web("#ee8054"), CategoricalColorPalette.colorAt(15));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> CategoricalColorPalette.colorAt(16));
    }

    /** colorForIndex cycles: index 16 is slot 0 again, and -1 is the last slot rather than an error. */
    @Test
    public void colorForIndexCycles() {
        int size = CategoricalColorPalette.size();
        for (int i = 0; i < size; i++)
            assertEquals(CategoricalColorPalette.colorAt(i), CategoricalColorPalette.colorForIndex(i));
        assertEquals(CategoricalColorPalette.colorAt(0), CategoricalColorPalette.colorForIndex(size));
        assertEquals(CategoricalColorPalette.colorAt(3), CategoricalColorPalette.colorForIndex(2 * size + 3));
        assertEquals(CategoricalColorPalette.colorAt(size - 1), CategoricalColorPalette.colorForIndex(-1));
    }

    /** The background tint is three quarters of the way to white and keeps the color's hue. */
    @Test
    public void backgroundTintMovesTowardsWhite() {
        Color base = CategoricalColorPalette.colorAt(0);
        Color tint = CategoricalColorPalette.tintForBackground(base);
        assertEquals(base.getRed() + 0.75 * (1 - base.getRed()), tint.getRed(), 1e-6);
        assertEquals(base.getGreen() + 0.75 * (1 - base.getGreen()), tint.getGreen(), 1e-6);
        assertEquals(base.getBlue() + 0.75 * (1 - base.getBlue()), tint.getBlue(), 1e-6);
        assertTrue(tint.getBrightness() > base.getBrightness());
        assertEquals(base.getHue(), tint.getHue(), 1.0);
        assertEquals(Color.WHITE, CategoricalColorPalette.tintForBackground(Color.WHITE));
    }
}
