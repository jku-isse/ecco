package at.jku.isse.ecco.storage.ser.featuretrace.evaluation;

import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.evaluation.RetroactiveBasedEvaluation;

import java.io.Serializable;

public class SerRetroactiveBasedEvaluation implements RetroactiveBasedEvaluation, Serializable {

    private static final long serialVersionUID = -1655533145028115988L;

    // the retroactive condition only
    @Override
    public boolean holds(Configuration configuration,
                         LogicCondition proactiveCondition,
                         LogicCondition retroactiveCondition){
        return retroactiveCondition != null && retroactiveCondition.evaluate(configuration.toAssignment());
    }

    @Override
    public LogicCondition getOverallCondition(LogicCondition proactiveCondition, LogicCondition retroactiveCondition) {
        return retroactiveCondition != null ? retroactiveCondition : LogicCondition.FALSE;
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
