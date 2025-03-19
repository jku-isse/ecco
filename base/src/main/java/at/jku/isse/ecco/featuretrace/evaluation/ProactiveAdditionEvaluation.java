package at.jku.isse.ecco.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;

/**
 * Represents a disjunction of retroactive and proactive condition.
 * (in a tree-composition, the proactive condition can lead to the addition of a node, but not a removal)
 */
public class ProactiveAdditionEvaluation implements EvaluationStrategy{

    private final String STRATEGY_NAME = "PROACTIVE-ADDITION";

    @Override
    public boolean holds (Configuration configuration,
                          String proactiveCondition,
                          String retroactiveCondition){
        Assignment assignment = configuration.toAssignment(this.formulaFactory);
        Formula formula = this.getOverallFormula(proactiveCondition, retroactiveCondition);
        return formula.evaluate(assignment);
    }

    @Override
    public String getOverallConditionString(String proactiveCondition, String retroactiveCondition) {
        Formula overallCondition = this.getOverallFormula(proactiveCondition, retroactiveCondition);
        return overallCondition.toString();
    }

    private Formula getOverallFormula(String proactiveCondition, String retroactiveCondition){
        Formula retroactiveFormula = null;
        Formula proactiveFormula = null;
        if (retroactiveCondition != null){ retroactiveFormula = LogicUtils.parseString(this.formulaFactory, retroactiveCondition); }
        if (proactiveCondition != null){ proactiveFormula = LogicUtils.parseString(this.formulaFactory, proactiveCondition); }

        assert(retroactiveFormula != null || proactiveFormula != null);
        if (retroactiveFormula != null && proactiveFormula != null){
            return this.formulaFactory.or(retroactiveFormula, proactiveFormula);
        } else if (proactiveFormula != null){
            return proactiveFormula;
        } else {
            return retroactiveFormula;
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
