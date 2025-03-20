package at.jku.isse.ecco.experiment.mistake;

import at.jku.isse.ecco.featuretrace.FeatureTrace;


public class NoMistake extends MistakeStrategy {
    @Override
    public String createNewMistake(FeatureTrace trace) {
        return trace.getProactiveConditionString();
    }

    @Override
    public String toString(){
        return "NoMistake";
    }
}
