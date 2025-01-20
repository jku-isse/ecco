package at.jku.isse.ecco.experiment.result.persister;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.result.Result;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;


public interface ResultPersister {
    void persist(Result result, ExperimentRunConfiguration config, int featureTracePercentage, int mistakePercentage,
                 EvaluationStrategy evaluationStrategy, String mistakeStrategy, boolean boosting, int numberOfMissingMistakes,
                 String listPicker);
}
