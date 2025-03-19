package at.jku.isse.ecco.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;
import org.logicng.formulas.FormulaFactory;

public interface EvaluationStrategy {

    FormulaFactory formulaFactory = new FormulaFactory();

    boolean holds(Configuration configuration,
                  String proactiveCondition,
                  String retroactiveCondition);

    String getOverallConditionString(String proactiveCondition, String retroactiveCondition);

    String getStrategyName();
}
