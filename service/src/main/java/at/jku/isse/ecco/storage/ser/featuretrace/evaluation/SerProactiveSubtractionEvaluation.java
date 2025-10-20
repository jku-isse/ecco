package at.jku.isse.ecco.storage.ser.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.logic.LogicUtils;
import at.jku.isse.ecco.featuretrace.evaluation.ProactiveSubtractionEvaluation;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;

import java.io.Serializable;

public class SerProactiveSubtractionEvaluation implements ProactiveSubtractionEvaluation, Serializable {

    @Override
    public boolean holds (Configuration configuration,
                          String proactiveCondition,
                          String retroactiveCondition){
        Assignment assignment = configuration.toAssignment();
        Formula overallFormula = this.getOverallFormula(proactiveCondition, retroactiveCondition);
        return overallFormula.evaluate(assignment);
    }

    private Formula getOverallFormula(String proactiveCondition, String retroactiveCondition) {
        Formula retroactiveFormula = null;
        Formula proactiveFormula = null;
        if (retroactiveCondition != null){ retroactiveFormula = LogicUtils.parseString(retroactiveCondition); }
        if (proactiveCondition != null){ proactiveFormula = LogicUtils.parseString(proactiveCondition); }

        if (retroactiveFormula == null){
            return FormulaFactoryProvider.getFormulaFactory().constant(false);
        } else if (proactiveFormula != null){
            return FormulaFactoryProvider.getFormulaFactory().and(retroactiveFormula, proactiveFormula);
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
        return ProactiveSubtractionEvaluation.STRATEGY_NAME;
    }

    @Override
    public String toString(){
        return this.getStrategyName();
    }
}
