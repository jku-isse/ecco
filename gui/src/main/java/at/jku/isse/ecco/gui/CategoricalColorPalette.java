package at.jku.isse.ecco.gui;

import javafx.scene.paint.Color;

/**
 * Fixed-order categorical palette shared by every view that automatically assigns distinct colors
 * to an open-ended set of items (associations in {@link at.jku.isse.ecco.gui.view.artifacts.ArtifactsView},
 * artifact kinds in {@link at.jku.isse.ecco.gui.view.detail.ArtifactSnippetTreeView}, ...).
 * <p>
 * The first 8 slots are the primary hues; order is the color-vision-deficiency-safety mechanism
 * (not cosmetic), maximizing the minimum adjacent perceptual distance. Slots 8-15 are a second,
 * per-hue shade (lightened or darkened, whichever direction had headroom within the light-mode
 * OKLCH band - some hues, e.g. violet, are already close to the band floor and can't go darker)
 * chosen to maximize its worst-case CVD separation against all 15 other slots. Both tiers were
 * generated and validated with the dataviz skill's palette validator: all 16 pass the lightness
 * band and chroma floor checks; the worst all-pairs CVD separation (a shade against its own base
 * hue) sits in the 8-12 "floor" band, which the skill allows only with secondary encoding - every
 * caller here always shows the item's id/label text alongside the color, never color-alone, which
 * satisfies that. Light-surface text contrast WARNs on several slots, which is expected and fine
 * since these are used as fills behind dark text / small identity swatches, not as text color.
 * <p>
 * Beyond 16 items there is no way to keep manufacturing safely distinct hues (validated further
 * out, the closest surviving pairs kept dropping below even the floor band), so the palette cycles
 * - repeats are an accepted, honest tradeoff there, not a defect.
 */
public final class CategoricalColorPalette {

	private static final Color[] COLORS = {
			Color.web("#2a78d6"), // blue
			Color.web("#1baf7a"), // aqua
			Color.web("#eda100"), // yellow
			Color.web("#008300"), // green
			Color.web("#4a3aa7"), // violet
			Color.web("#e34948"), // red
			Color.web("#e87ba4"), // magenta
			Color.web("#eb6834"), // orange
			Color.web("#7babe6"), // blue, light shade
			Color.web("#179568"), // aqua, dark shade
			Color.web("#c98900"), // yellow, dark shade
			Color.web("#6bb76b"), // green, light shade
			Color.web("#8277c2"), // violet, light shade
			Color.web("#882c2b"), // red, dark shade
			Color.web("#99516c"), // magenta, dark shade
			Color.web("#ee8054"), // orange, light shade
	};

	/** Fallback for callers that stop assigning distinct slots past {@link #size()} rather than cycling. */
	public static final Color OTHER = Color.web("#898781");

	private static final double DEFAULT_BACKGROUND_TINT = 0.75;

	private CategoricalColorPalette() {
	}

	public static int size() {
		return COLORS.length;
	}

	public static Color colorAt(int index) {
		return COLORS[index];
	}

	/**
	 * Cycles through the palette for {@code index >= size()}, for callers where every item needs
	 * its own color and there's no natural "everything else" bucket (unlike, say, artifact kinds).
	 */
	public static Color colorForIndex(int index) {
		return COLORS[Math.floorMod(index, COLORS.length)];
	}

	/**
	 * Tints a palette color towards white, for use as a soft background fill behind readable text
	 * (e.g. a highlighted code line or tree row) rather than a small, solid identity swatch (e.g.
	 * a legend marker or color-picker preview), where the full saturated color reads better.
	 */
	public static Color tintForBackground(Color color) {
		return color.interpolate(Color.WHITE, DEFAULT_BACKGROUND_TINT);
	}
}
