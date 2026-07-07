package at.jku.isse.ecco.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;

public interface EvaluationStrategy {

    boolean holds(Configuration configuration,
                  String proactiveCondition,
                  String retroactiveCondition);

    String getOverallConditionString(String proactiveCondition, String retroactiveCondition);

    String getStrategyName();
}
