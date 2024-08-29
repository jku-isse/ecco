package at.jku.isse.ecco.featuretrace;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.module.ModuleRevision;

import java.util.Collection;

/**
 * A condition in order to check feature traces to be applied for given configurations.
 * One positive modules must hold and no negative module must hold for a condition to hold.
 *
 * There must not be multiple revisions of the same Module.
 */
public interface FeatureTraceCondition extends Persistable {

    boolean holds(Configuration configuration);

    Collection<ModuleRevision> getAllModuleRevisions();

    Collection<ModuleRevision> getPositiveModuleRevisions();

    Collection<ModuleRevision> getNegativeModuleRevisions();

    FeatureTraceCondition copy();

    void addAllPositiveModuleRevisions(Collection<ModuleRevision> moduleRevisions);

    void addAllNegativeModuleRevisions(Collection<ModuleRevision> moduleRevisions);

    static FeatureTraceCondition merge(FeatureTraceCondition condition1, FeatureTraceCondition condition2) {
        if (condition1 == null && condition2 == null){
            return null;
        } else if (condition1 == null){
            return condition2.copy();
        } else if (condition2 == null){
            return condition1.copy();
        }

        // any positive module revision of either feature trace condition must hold in order to hold
        // neither negative module revision of either feature trace condition must hold in order to hold
        // (merging means merging the conditions of nested artifacts in the nested artifact)
        // (if the outer artifact condition is not met, the inner is not as well)
        // (if the inner artifact condition is not met, the inner one is not met) (duh)

        FeatureTraceCondition condition = condition1.copy();
        condition.addAllPositiveModuleRevisions(condition2.getPositiveModuleRevisions());
        condition.addAllNegativeModuleRevisions(condition2.getNegativeModuleRevisions());
        return condition;
    }

    @Override
    boolean equals(Object o);

    @Override
    int hashCode();
}
