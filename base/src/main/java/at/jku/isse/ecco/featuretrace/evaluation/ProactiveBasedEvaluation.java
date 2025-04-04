package at.jku.isse.ecco.featuretrace.evaluation;


/**
 * The proactive condition of a feature trace determines the overall condition.
 * The retroactive condition is ignored.
 */
public interface ProactiveBasedEvaluation extends EvaluationStrategy{
    String STRATEGY_NAME = "PROACTIVE";
}
