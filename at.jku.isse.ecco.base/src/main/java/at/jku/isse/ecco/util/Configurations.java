package at.jku.isse.ecco.util;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureInstance;

import java.util.stream.Collectors;

/**
 * This static class provides a collection of configuration utility functions.
 */
public class Configurations {

	private Configurations() {
	}


	/**
	 * Creates the configuration string for the given configuration.
	 *
	 * @param configuration The configuration object.
	 * @return The configuration string.
	 */
	public static String createConfigurationString(Configuration configuration) {
		// TODO: put this into Configuration.toString()
		return configuration.getFeatureInstances().stream().map((FeatureInstance fi) -> {
			StringBuffer sb = new StringBuffer();
			if (fi.getSign())
				sb.append("+");
			else
				sb.append("-");
			sb.append(fi.getFeatureVersion().getFeature().getName());
			sb.append(".");
			sb.append(fi.getFeatureVersion());
			return sb.toString();
		}).collect(Collectors.joining(", "));
	}

}
