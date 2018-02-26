package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.Persistable;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A configuration of a variant that consists of a set of feature revisions that are selected in the variant.
 * A configuration can be specified by means of a configuration string.
 * The format of the configuration string must match the regular expression {@link #CONFIGURATION_STRING_REGULAR_EXPRESSION}.
 * Examples of valid configuration strings:
 * <ul>
 * <li>NAME_A, NAME_B, NAME_C</li>
 * <li>NAME_A.1, NAME_B.1, NAME_C.1</li>
 * <li>NAME_A.1, NAME_B.2, NAME_C</li>
 * <li>NAME_A.1, NAME_B'</li>
 * <li>[ID_A].1, [ID_B].2</li>
 * </ul>
 */
public interface Configuration extends Persistable {

	public static final String CONFIGURATION_STRING_REGULAR_EXPRESSION = "(((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))('|(\\.([a-zA-Z0-9_-])+))?(\\s*,\\s*((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))('|(\\.([a-zA-Z0-9_-])+))?)*)?";

	public FeatureRevision[] getFeatureRevisions();

	public default String getConfigurationString() {
		return Arrays.stream(this.getFeatureRevisions()).map(featureRevision -> featureRevision.toString()).collect(Collectors.joining(", "));
	}

	/**
	 * Should call {@link #getConfigurationString}.
	 *
	 * @return The configuration string representing this configuration.
	 */
	@Override
	public String toString();

}
