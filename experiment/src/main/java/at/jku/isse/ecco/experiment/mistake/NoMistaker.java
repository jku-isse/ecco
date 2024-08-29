package at.jku.isse.ecco.experiment.mistake;

import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;


public class NoMistaker implements MistakeStrategy {
    @Override
    public void createMistake(FeatureTrace trace) {}

    @Override
    public void init(Repository.Op repository){}

    @Override
    public String toString(){
        return "NoMistaker";
    }
}
