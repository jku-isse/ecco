package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.Persistable;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Literal;

import java.util.*;
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
    String CONFIGURATION_STRING_REGULAR_EXPRESSION = ConfigurationText.REGULAR_EXPRESSION;

    /**
     * Returns a direct reference to the instance of the array of feature revisions that makes up the configuration.
     * DO NOT MODIFY THIS ARRAY!!!
     * TREAT THE RETURNED ARRAY AS CONST!!!
     *
     * @return The array of feature revisions that makes up the configuration.
     */
    FeatureRevision[] getFeatureRevisions();

    Configuration getConfiguration();

    void setOriginalConfigString(String originalConfigString);

    String getOriginalConfigString();

    void setFeatureRevisions(FeatureRevision[] featureRevisions);

    /**
     * The assignment feature-trace conditions are evaluated with: for each selected feature revision,
     * its revision ("name_id", as retroactive conditions name it) and its feature ("name", as
     * proactive conditions such as VEVOS presence conditions name it) are true. Names are sanitized
     * like condition strings ('.' and '-' become '_') - see {@link FeatureLiterals}.
     */
    default Assignment toAssignment(){
        return FeatureLiterals.assignment(this);
    }

    @Override
    int hashCode();

    @Override
    boolean equals(Object object);


    default String getConfigurationString() {
        // sorted: the revisions' order is no part of a configuration (and came from a hash set)
        return ConfigurationText.print(Arrays.asList(this.getFeatureRevisions()), false);
    }

    /**
     * Should call {@link #getConfigurationString}.
     *
     * @return The configuration string representing this configuration.
     */
    @Override
    String toString();

}
