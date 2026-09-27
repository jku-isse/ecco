package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.service.EccoService;

import java.util.List;

/**
 * The one-line constraint warning shown next to a configuration in the commit, checkout, variants
 * and git-import views. Constraint violations are advisory (see
 * {@code EccoService#checkConstraintViolations}), so a failing check yields no warning rather than
 * an error.
 */
public final class ConstraintWarnings {

	private ConstraintWarnings() {
	}

	/** Empty string if no violations (or the configuration can't be parsed, e.g. mid-typing). */
	public static String describe(EccoService service, String configurationString) {
		try {
			return describe(service, service.parseConfigurationString(configurationString));
		} catch (RuntimeException e) {
			return "";
		}
	}

	/** Empty string if no violations. */
	public static String describe(EccoService service, Configuration configuration) {
		try {
			List<String> violations = service.checkConstraintViolations(configuration);
			return violations.isEmpty() ? "" : "Violates accepted constraint(s): " + String.join("; ", violations);
		} catch (RuntimeException e) {
			return "";
		}
	}
}
