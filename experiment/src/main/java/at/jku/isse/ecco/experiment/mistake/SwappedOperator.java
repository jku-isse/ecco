package at.jku.isse.ecco.experiment.mistake;

import at.jku.isse.ecco.featuretrace.FeatureTrace;

public class SwappedOperator extends MistakeStrategy {
    @Override
    public String createNewMistake(FeatureTrace trace) {
        try {
            String oldCondition = trace.getProactiveConditionString();
            String newCondition;
            if (oldCondition.contains("|")) {
                newCondition = oldCondition.replaceFirst("\\|", "&");
            } else if (oldCondition.contains("&")) {
                newCondition = oldCondition.replaceFirst("&", "|");
            } else {
                throw new RuntimeException("Featuretrace contains no Operator.");
            }
            trace.setProactiveCondition(newCondition);
            return newCondition;
        } catch (Exception e){
            throw new RuntimeException("SwappedOperator failed to create mistake.");
        }
    }

    @Override
    public String toString(){
        return "SwappedOperator";
    }
}
