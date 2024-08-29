package at.jku.isse.ecco.experiment.result.persister;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.result.Result;


public interface ResultPersister {
    void persist(Result result, ExperimentRunConfiguration config);
}
