package at.jku.isse.ecco.feature;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A configuration of a variant that consists of a set of feature instances.
 * A configuration defines a set of features. When committing a product, the configuration associated to the product
 * can be provided. The format of the configuration description must comply to the regular expression {@link #CONFIGURATION_STRING_REGULAR_EXPRESSION}.
 * The configuration syntax allows representing features in the following way:
 * <p>
 * <ul>
 * <li>
 * </ul>
 * <p>
 */
public interface Configuration {

	public static final String CONFIGURATION_STRING_REGULAR_EXPRESSION = "((\\+|\\-)?((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))('?|(\\.([a-zA-Z0-9_-])+)?)(\\s*,\\s*(\\+|\\-)?((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))('?|(\\.([a-zA-Z0-9_-])+)?))*)?";

	public FeatureRevision[] getFeatureRevisions();

	public default String getConfigurationString() {
		return Arrays.stream(this.getFeatureRevisions()).map(fi -> fi.toString()).collect(Collectors.joining(", "));
	}

	@Override
	public String toString();

}
