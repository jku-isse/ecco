package at.jku.isse.ecco.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;

/**
 * Represents a conjunction of retroactive and proactive condition.
 * (in a tree-composition, the proactive condition can lead to the removal of a node, but not an addition)
 * (if there is no retroactive condition, it evaluates to false)
 */
public class ProactiveSubtractionEvaluation implements EvaluationStrategy{

    private final String STRATEGY_NAME = "PROACTIVE-SUBTRACTION";

    @Override
    public boolean holds (Configuration configuration,
                          String proactiveCondition,
                          String retroactiveCondition){
        Assignment assignment = configuration.toAssignment(this.formulaFactory);
        Formula overallFormula = this.getOverallFormula(proactiveCondition, retroactiveCondition);
        return overallFormula.evaluate(assignment);
    }

    private Formula getOverallFormula(String proactiveCondition, String retroactiveCondition) {
        Formula retroactiveFormula = null;
        Formula proactiveFormula = null;
        if (retroactiveCondition != null){ retroactiveFormula = LogicUtils.parseString(this.formulaFactory, retroactiveCondition); }
        if (proactiveCondition != null){ proactiveFormula = LogicUtils.parseString(this.formulaFactory, proactiveCondition); }

        if (retroactiveFormula == null){
            return this.formulaFactory.constant(false);
        } else if (proactiveFormula != null){
            return this.formulaFactory.and(retroactiveFormula, proactiveFormula);
        } else {
            return retroactiveFormula;
        }
    }

    @Override
    public String getOverallConditionString(String proactiveCondition, String retroactiveCondition) {
        Formula overallFormula = this.getOverallFormula(proactiveCondition, retroactiveCondition);
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
