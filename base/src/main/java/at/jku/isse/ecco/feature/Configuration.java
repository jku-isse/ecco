package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
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
    String CONFIGURATION_STRING_REGULAR_EXPRESSION = "(((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))('|(\\.([a-zA-Z0-9_-])+))?(\\s*,\\s*((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))('|(\\.([a-zA-Z0-9_-])+))?)*)?";

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

    default boolean contains(Module module) {
        // check if all positive features of the module are contained in the configuration
        for (Feature feature : module.getPos()) {
            boolean found = false;
            for (FeatureRevision confFeatureRevision : this.getFeatureRevisions()) {
                if (confFeatureRevision.getFeature().equals(feature)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        // check if no negative features of the module are contained in the configuration
        for (Feature feature : module.getNeg()) {
            for (FeatureRevision confFeatureRevision : this.getFeatureRevisions()) {
                if (confFeatureRevision.getFeature().equals(feature)) {
                    return false;
                }
            }
        }
        return true;
    }

    default boolean contains(ModuleRevision moduleRevision) {
        // check if all positive features revisiosn of the module are contained in the configuration
        for (FeatureRevision featureRevision : moduleRevision.getPos()) {
            boolean found = false;
            for (FeatureRevision confFeatureRevision : this.getFeatureRevisions()) {
                if (confFeatureRevision.equals(featureRevision)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        // check if no negative features of the module are contained in the configuration
        for (Feature feature : moduleRevision.getNeg()) {
            for (FeatureRevision confFeatureRevision : this.getFeatureRevisions()) {
                if (confFeatureRevision.getFeature().equals(feature)) {
                    return false;
                }
            }
        }
        return true;
    }

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
        return Arrays.stream(this.getFeatureRevisions()).map(FeatureRevision::toString).sorted().collect(Collectors.joining(", "));
    }

    /**
     * Should call {@link #getConfigurationString}.
     *
     * @return The configuration string representing this configuration.
     */
    @Override
    String toString();

}
