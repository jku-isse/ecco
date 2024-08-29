package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.repository.Repository;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.FormulaFactory;
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

    default Set<ModuleRevision> computeModules(int maxOrder, Repository.Op repository, Configuration configuration) {
        Set<ModuleRevision> desiredModules = new HashSet<>();
        FeatureRevision[] featuresRevisions = configuration.getFeatureRevisions();
        ArrayList<Feature> features = new ArrayList<>();
        for (FeatureRevision featurerevision : featuresRevisions) {
            features.add(featurerevision.getFeature());
        }
        for (Association association : repository.getAssociations()) {
            Condition moduleCondition = association.computeCondition();
            if (moduleCondition.getType() == Condition.TYPE.AND) {
                Map<Module, Collection<ModuleRevision>> moduleMap = moduleCondition.getModules();
                for (Map.Entry<Module, Collection<ModuleRevision>> entry : moduleMap.entrySet()) {
                    if (entry.getValue() != null) {
                        for (ModuleRevision existingModuleRevision : entry.getValue()) {
                            Boolean addmodule = true;
                            for (Feature negfeat : existingModuleRevision.getNeg()) {
                                if (features.contains(negfeat)) {
                                    addmodule = false;
                                    break;
                                }
                            }
                            if (addmodule) {
                                FeatureRevision[] featmodule = existingModuleRevision.getPos();
                                for (FeatureRevision featm : featmodule) {
                                    if (Arrays.stream(featuresRevisions).anyMatch(featureRevision -> featureRevision.equals(featm))) {
                                        //System.out.println(featm);
                                        desiredModules.add(existingModuleRevision);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return desiredModules;
    }

    default Set<ModuleRevision> computeModulesMissing(int maxOrder, Repository.Op repository, Configuration configuration) {
        Set<ModuleRevision> missinModules = new HashSet<>();
        FeatureRevision[] featuresRevisions = configuration.getFeatureRevisions();
        List<FeatureRevision> featrev =Arrays.asList(configuration.getFeatureRevisions());
        ArrayList<Feature> features = new ArrayList<>();
        for (FeatureRevision featurerevision : featuresRevisions) {
            features.add(featurerevision.getFeature());
        }
        for (Association association : repository.getAssociations()) {
            Condition moduleCondition = association.computeCondition();
            Map<Module, Collection<ModuleRevision>> moduleMap = moduleCondition.getModules();
            for (Map.Entry<Module, Collection<ModuleRevision>> entry : moduleMap.entrySet()) {
                if (entry.getValue() != null) {
                    for (ModuleRevision existingModuleRevision : entry.getValue()) {
                        Boolean addmodule = false;
                        for (FeatureRevision posfeat : existingModuleRevision.getPos()) {
                            if (featrev.contains(posfeat)) {
                                addmodule = true;
                                break;
                            }
                        }
                        if (addmodule) {
                            Feature[] negfeatmodule = existingModuleRevision.getNeg();
                            for (Feature feat : negfeatmodule) {
                                if (!features.contains(feat))
                                    //System.out.println(featm);
                                    missinModules.add(existingModuleRevision);
                            }
                        }
                    }
                }
            }
        }

        return missinModules;
    }

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

    default Assignment toAssignment(FormulaFactory factory){
        Assignment assignment = new Assignment();
        for (FeatureRevision featureRevision: this.getFeatureRevisions()){
            // the specific revision is true
            String literalString = featureRevision.getFeature().getName() + "." + featureRevision.getId();
            literalString = literalString.replace(".", "_");
            literalString = literalString.replace("-", "_");
            Literal revisionLiteral = factory.literal(literalString, true);
            assignment.addLiteral(revisionLiteral);
        }
        return assignment;
    }

    @Override
    int hashCode();

    @Override
    boolean equals(Object object);


    default String getConfigurationString() {
        return Arrays.stream(this.getFeatureRevisions()).map(FeatureRevision::toString).collect(Collectors.joining(", "));
    }

    /**
     * Should call {@link #getConfigurationString}.
     *
     * @return The configuration string representing this configuration.
     */
    @Override
    String toString();

}
