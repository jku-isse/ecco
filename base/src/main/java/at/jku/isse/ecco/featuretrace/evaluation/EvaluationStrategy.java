package at.jku.isse.ecco.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;

import java.io.Serializable;

public interface EvaluationStrategy extends Serializable {

    boolean holds(Configuration configuration,
                  String proactiveCondition,
                  String retroactiveCondition);

    String getOverallConditionString(String proactiveCondition, String retroactiveCondition);

    String getStrategyName();
}
