package at.jku.isse.ecco.service;

/**
 * The switches of a checkout's warnings and conditions, set through {@link EccoService}
 * ({@code setSurplusSuppressionEnabled} and the others, which say what each does) and read by
 * {@link CheckoutService} and {@link ConstraintService}. Volatile: set while a checkout may run.
 */
final class CheckoutOptions {

	volatile boolean surplusSuppressionEnabled = true;

	volatile boolean surplusAbsorptionEnabled = true;

	volatile boolean constraintViolationWarningsEnabled = true;

	// off by default: checkout uses the associations' own conditions unless asked otherwise - see
	// EccoService#setMinimizedConditionsInCheckout
	volatile boolean minimizedConditionsInCheckout = false;
}
