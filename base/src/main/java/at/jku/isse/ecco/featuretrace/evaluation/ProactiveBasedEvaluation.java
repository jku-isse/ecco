package at.jku.isse.ecco.featuretrace.evaluation;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;

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
        Assignment assignment = configuration.toAssignment(this.formulaFactory);
        assert(proactiveCondition != null);
        Formula proactiveFormula = LogicUtils.parseString(this.formulaFactory, proactiveCondition);
        return proactiveFormula.evaluate(assignment);
    }

    @Override
    public String getOverallConditionString(String proactiveCondition, String retroactiveCondition) {
        if (proactiveCondition == null && retroactiveCondition == null){
            Formula falseFormula = this.formulaFactory.constant(false);
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
