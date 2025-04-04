package at.jku.isse.ecco.featuretrace.evaluation;


/**
 * Represents a disjunction of retroactive and proactive condition.
 * (in a tree-composition, the proactive condition can lead to the addition of a node, but not a removal)
 */
public interface ProactiveAdditionEvaluation extends EvaluationStrategy{
    String STRATEGY_NAME = "PROACTIVE-ADDITION";
}
