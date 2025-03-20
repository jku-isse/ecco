package at.jku.isse.ecco.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

/**
 * The proactive condition of a feature trace determines the overall condition.
 * The retroactive condition is ignored.
 */
public class ProactiveBasedEvaluation implements EvaluationStrategy{

    private final String STRATEGY_NAME = "PROACTIVE";

    @Override
    public boolean holds(Configuration configuration,
                         String proactiveCondition,
                         String retroactiveCondition) {
        Assignment assignment = configuration.toAssignment();
        String overallConditionString = this.getOverallConditionString(proactiveCondition, retroactiveCondition);
        Formula formula = LogicUtils.parseString(overallConditionString);
        boolean x = formula.evaluate(assignment);
        return x;
    }

    @Override
    public String getOverallConditionString(String proactiveCondition, String retroactiveCondition) {
        if (proactiveCondition == null && retroactiveCondition == null){
            Formula falseFormula = FormulaFactoryProvider.getFormulaFactory().constant(false);
            return falseFormula.toString();
        } else if (proactiveCondition == null){
            return retroactiveCondition;
        } else {
            return proactiveCondition;
        }
    }

    @Override
    public String getStrategyName(){
        return this.STRATEGY_NAME;
    }

    @Override
    public String toString(){
        return this.getStrategyName();
    }
}
