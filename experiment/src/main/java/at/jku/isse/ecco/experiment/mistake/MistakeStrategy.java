package at.jku.isse.ecco.experiment.mistake;

import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;


public interface MistakeStrategy {
    void createMistake(FeatureTrace trace);
    void init(Repository.Op repository);
}
