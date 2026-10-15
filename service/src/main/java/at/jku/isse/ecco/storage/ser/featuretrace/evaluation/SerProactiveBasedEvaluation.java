package at.jku.isse.ecco.storage.ser.featuretrace.evaluation;

import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.evaluation.ProactiveBasedEvaluation;

import java.io.Serializable;

public class SerProactiveBasedEvaluation implements ProactiveBasedEvaluation, Serializable {

    private static final long serialVersionUID = 6215009445454827750L;

    // the proactive condition where there is one, otherwise the retroactive one
    @Override
    public boolean holds(Configuration configuration,
                         LogicCondition proactiveCondition,
                         LogicCondition retroactiveCondition) {
        return this.getOverallCondition(proactiveCondition, retroactiveCondition).evaluate(configuration.toAssignment());
    }

    @Override
    public LogicCondition getOverallCondition(LogicCondition proactiveCondition, LogicCondition retroactiveCondition) {
        if (proactiveCondition != null)
            return proactiveCondition;
        return retroactiveCondition != null ? retroactiveCondition : LogicCondition.FALSE;
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
