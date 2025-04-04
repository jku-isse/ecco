package at.jku.isse.ecco.featuretrace.evaluation;


/**
 * Represents a conjunction of retroactive and proactive condition.
 * (in a tree-composition, the proactive condition can lead to the removal of a node, but not an addition)
 * (if there is no retroactive condition, it evaluates to false)
 */
public interface ProactiveSubtractionEvaluation extends EvaluationStrategy{
    String STRATEGY_NAME = "PROACTIVE-SUBTRACTION";
}
