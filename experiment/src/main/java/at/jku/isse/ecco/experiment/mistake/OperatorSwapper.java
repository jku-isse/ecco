package at.jku.isse.ecco.experiment.mistake;

import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;

public class OperatorSwapper implements MistakeStrategy {
    @Override
    public void createMistake(FeatureTrace trace) {
        try {
            String oldCondition = trace.getUserConditionString();
            String newCondition;
            if (oldCondition.contains("|")) {
                newCondition = oldCondition.replaceFirst("\\|", "&");
            } else if (oldCondition.contains("&")) {
                newCondition = oldCondition.replaceFirst("&", "|");
            } else {
                throw new RuntimeException("Featuretrace contains no Operator.");
            }
            trace.setUserCondition(newCondition);
        } catch (Exception e){
            throw new RuntimeException("OperatorSwapper failed to create mistake.");
        }
    }

    @Override
    public void init(Repository.Op repository) {}

    @Override
    public String toString(){
        return "OperatorSwapper";
    }
}
