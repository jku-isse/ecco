package at.jku.isse.ecco.experiment.mistake;

import at.jku.isse.ecco.experiment.utils.CollectionUtils;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;

import java.util.Collection;
import java.util.stream.Collectors;

public class ConditionSwapper extends MistakeStrategy {

    Collection<String> originalConditions;
    boolean initFlag = false;

    @Override
    public String createNewMistake(FeatureTrace trace) {
        try {
            if (!this.initFlag){
                throw new RuntimeException("init() not called before creating a mistake in ConditionSwapper.");
            }
            String oldCondition = trace.getProactiveConditionString();
            Collection<String> differentConditions = this.originalConditions.stream().filter(c -> !oldCondition.equals(c)).collect(Collectors.toSet());
            if (differentConditions.size() == 0){
                throw new RuntimeException("There are no different conditions to swap with.");
            }
            String newCondition = CollectionUtils.getRandom(differentConditions);
            trace.setProactiveCondition(newCondition);
            return newCondition;
        } catch (Exception e){
            throw new RuntimeException("ConditionSwapper failed to create mistake.");
        }
    }

    @Override
    public void init(Repository.Op repository) {
        this.initFlag = true;
        Collection<FeatureTrace> featureTraces = repository.getFeatureTraces();
        this.originalConditions = featureTraces.stream().map(FeatureTrace::getProactiveConditionString).collect(Collectors.toList());
    }

    @Override
    public String toString(){
        String description = "ConditionSwapper";
        if (this.initFlag){
            description += " (Original Conditions: " + this.originalConditions + ")";
        }
        return description;
    }
}
