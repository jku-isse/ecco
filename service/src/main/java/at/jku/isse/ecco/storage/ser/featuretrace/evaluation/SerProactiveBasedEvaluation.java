package at.jku.isse.ecco.storage.ser.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.logic.LogicUtils;
import at.jku.isse.ecco.featuretrace.evaluation.ProactiveBasedEvaluation;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;

import java.io.Serializable;

public class SerProactiveBasedEvaluation implements ProactiveBasedEvaluation, Serializable {

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
        return ProactiveBasedEvaluation.STRATEGY_NAME;
    }

    @Override
    public String toString(){
        return this.getStrategyName();
    }
}
