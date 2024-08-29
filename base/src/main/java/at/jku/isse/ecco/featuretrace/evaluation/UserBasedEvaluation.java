package at.jku.isse.ecco.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

/**
 * The user-based condition of a feature trace determines the overall condition.
 * The diff-based condition is ignored.
 */
public class UserBasedEvaluation implements EvaluationStrategy{

    private final String STRATEGY_NAME = "USER-BASED";

    @Override
    public boolean holds(Configuration configuration,
                         String userCondition,
                         String diffCondition) {
        Assignment assignment = configuration.toAssignment(this.formulaFactory);
        assert(userCondition != null);
        Formula userFormula = LogicUtils.parseString(this.formulaFactory, userCondition);
        return userFormula.evaluate(assignment);
    }

    @Override
    public String getOverallConditionString(String userCondition, String diffCondition) {
        if (userCondition == null && diffCondition == null){
            Formula falseFormula = this.formulaFactory.constant(false);
            return falseFormula.toString();
        } else if (userCondition == null){
            return diffCondition;
        } else {
            return userCondition;
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
