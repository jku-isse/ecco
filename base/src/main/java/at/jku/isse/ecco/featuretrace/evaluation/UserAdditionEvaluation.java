package at.jku.isse.ecco.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

/**
 * Represents a disjunction of diff- and user-based condition.
 * (in a tree-composition, the user-based condition can lead to the addition of a node, but not a removal)
 */
public class UserAdditionEvaluation implements EvaluationStrategy{

    private final String STRATEGY_NAME = "USER-ADDITION";

    @Override
    public boolean holds (Configuration configuration,
                          String userCondition,
                          String diffCondition){
        Assignment assignment = configuration.toAssignment(this.formulaFactory);
        Formula formula = this.getOverallFormula(userCondition, diffCondition);
        return formula.evaluate(assignment);
    }

    @Override
    public String getOverallConditionString(String userCondition, String diffCondition) {
        Formula overallCondition = this.getOverallFormula(userCondition, diffCondition);
        return overallCondition.toString();
    }

    private Formula getOverallFormula(String userCondition, String diffCondition){
        Formula diffFormula = null;
        Formula userFormula = null;
        if (diffCondition != null){ diffFormula = LogicUtils.parseString(this.formulaFactory, diffCondition); }
        if (userCondition != null){ userFormula = LogicUtils.parseString(this.formulaFactory, userCondition); }

        assert(diffFormula != null || userFormula != null);
        if (diffFormula != null && userFormula != null){
            return this.formulaFactory.or(diffFormula, userFormula);
        } else if (userFormula != null){
            return userFormula;
        } else {
            return diffFormula;
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
