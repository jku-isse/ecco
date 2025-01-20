package at.jku.isse.ecco.experiment.mistake;

import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;

import java.util.HashMap;
import java.util.Map;



public abstract class MistakeStrategy {

    private final Map<String, String> mistakeMap = new HashMap<>();


    public void createMistake(FeatureTrace trace){
        String correctCondition = trace.getUserConditionString();
        if (correctCondition == null){
            throw new RuntimeException("Mistake cannot be added to nonexistent proactive user-trace.");
        }
        String faultyCondition = this.mistakeMap.get(correctCondition);
        if (faultyCondition != null){
            trace.setUserCondition(faultyCondition);
        } else {
            faultyCondition = this.createNewMistake(trace);
            this.mistakeMap.put(correctCondition, faultyCondition);
        }
    }

    protected abstract String createNewMistake(FeatureTrace trace);

    public void init(Repository.Op repository){

    }
}
