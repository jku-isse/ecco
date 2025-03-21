package at.jku.isse.ecco.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

/**
 * The retroactive condition of a feature trace determines the overall condition.
 * The proactive condition is ignored.
 */
public class RetroactiveBasedEvaluation implements EvaluationStrategy{

    public static final String STRATEGY_NAME = "RETROACTIVE";

    @Override
    public boolean holds(Configuration configuration,
                         String proactiveCondition,
                         String retroactiveCondition){
        Assignment assignment = configuration.toAssignment();
        if (retroactiveCondition == null){ return false; }
        Formula retroactiveFormula = LogicUtils.parseString(retroactiveCondition);
        return retroactiveFormula.evaluate(assignment);
    }

    @Override
    public String getOverallConditionString(String proactiveCondition, String retroactiveCondition) {
        if (retroactiveCondition == null){
            Formula falseFormula = FormulaFactoryProvider.getFormulaFactory().constant(false);
            return falseFormula.toString();
        } else {
            return retroactiveCondition;
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
