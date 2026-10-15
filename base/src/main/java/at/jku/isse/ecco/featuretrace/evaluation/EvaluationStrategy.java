package at.jku.isse.ecco.featuretrace.evaluation;

import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.feature.Configuration;

public interface EvaluationStrategy {

    /** Whether a node with these conditions (either may be absent: null) is in [configuration]. */
    boolean holds(Configuration configuration,
                  LogicCondition proactiveCondition,
                  LogicCondition retroactiveCondition);

    /** The condition a node with these conditions (either may be absent: null) is in a configuration under. */
    LogicCondition getOverallCondition(LogicCondition proactiveCondition, LogicCondition retroactiveCondition);

    String getStrategyName();
}
