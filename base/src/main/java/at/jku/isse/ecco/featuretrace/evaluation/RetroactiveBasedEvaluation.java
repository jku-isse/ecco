package at.jku.isse.ecco.featuretrace.evaluation;


/**
 * The retroactive condition of a feature trace determines the overall condition.
 * The proactive condition is ignored.
 */
public interface RetroactiveBasedEvaluation extends EvaluationStrategy{
    String STRATEGY_NAME = "RETROACTIVE";
}
