package at.jku.isse.ecco.storage.ser.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import at.jku.isse.ecco.featuretrace.evaluation.RetroactiveBasedEvaluation;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;

import java.io.Serializable;

public class SerRetroactiveBasedEvaluation implements RetroactiveBasedEvaluation, Serializable {

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
        return RetroactiveBasedEvaluation.STRATEGY_NAME;
    }

    @Override
    public String toString(){
        return this.getStrategyName();
    }
}
