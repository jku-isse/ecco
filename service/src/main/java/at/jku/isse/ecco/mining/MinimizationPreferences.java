package at.jku.isse.ecco.mining;

import java.util.prefs.Preferences;

/**
 * Persists the min-witness/confidence thresholds used to re-mine accepted constraints before
 * {@link ParallelMinimization} runs (see {@code at.jku.isse.ecco.gui.MinimizationResults}). Same
 * small, per-user runtime setting pattern as {@code at.jku.isse.ecco.service.LlmPreferences}
 * (backed by {@link Preferences}, e.g. the platform registry/plist) rather than the bundled
 * {@code ecco.properties} classpath resource, since this is a personal tuning knob, not part of
 * the repository format.
 */
public final class MinimizationPreferences {

	private static final String MIN_WITNESS_KEY = "minimizationMinWitness";
	private static final String CONFIDENCE_KEY = "minimizationConfidence";
	private static final String USE_IN_CHECKOUT_KEY = "minimizationUseInCheckout";

	// same defaults as SuggestConstraintsCommand/ConstraintSuggestionsView
	private static final int DEFAULT_MIN_WITNESS = 4;
	private static final double DEFAULT_CONFIDENCE = 0.9;

	private MinimizationPreferences() {
	}

	public static int getMinWitness() {
		return prefs().getInt(MIN_WITNESS_KEY, DEFAULT_MIN_WITNESS);
	}

	public static void setMinWitness(int minWitness) {
		prefs().putInt(MIN_WITNESS_KEY, minWitness);
	}

	public static double getConfidence() {
		return prefs().getDouble(CONFIDENCE_KEY, DEFAULT_CONFIDENCE);
	}

	public static void setConfidence(double confidence) {
		prefs().putDouble(CONFIDENCE_KEY, confidence);
	}

	/** Whether checkout uses minimized conditions (see EccoService#setMinimizedConditionsInCheckout); off by default. */
	public static boolean isUsedInCheckout() {
		return prefs().getBoolean(USE_IN_CHECKOUT_KEY, false);
	}

	public static void setUsedInCheckout(boolean used) {
		prefs().putBoolean(USE_IN_CHECKOUT_KEY, used);
	}

	private static Preferences prefs() {
		return Preferences.userNodeForPackage(MinimizationPreferences.class);
	}

}
