package at.jku.isse.ecco.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

/**
 * Represents a conjunction of diff- and user-based condition.
 * (in a tree-composition, the user-based condition can lead to the removal of a node, but not an addition)
 * (if there is no diff-based condition, it evaluates to false)
 */
public class UserSubtractionEvaluation implements EvaluationStrategy{

    private final String STRATEGY_NAME = "USER-SUBTRACTION";

    @Override
    public boolean holds (Configuration configuration,
                          String userCondition,
                          String diffCondition){
        Assignment assignment = configuration.toAssignment(this.formulaFactory);
        Formula overallFormula = this.getOverallFormula(userCondition, diffCondition);
        return overallFormula.evaluate(assignment);
    }

    private Formula getOverallFormula(String userCondition, String diffCondition) {
        Formula diffFormula = null;
        Formula userFormula = null;
        if (diffCondition != null){ diffFormula = LogicUtils.parseString(this.formulaFactory, diffCondition); }
        if (userCondition != null){ userFormula = LogicUtils.parseString(this.formulaFactory, userCondition); }

        if (diffFormula == null){
            return this.formulaFactory.constant(false);
        } else if (userFormula != null){
            return this.formulaFactory.and(diffFormula, userFormula);
        } else {
            return diffFormula;
        }
    }

    @Override
    public String getOverallConditionString(String userCondition, String diffCondition) {
        Formula overallFormula = this.getOverallFormula(userCondition, diffCondition);
        return overallFormula.toString();
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
