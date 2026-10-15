package at.jku.isse.ecco.storage.ser.featuretrace.evaluation;

import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.evaluation.ProactiveSubtractionEvaluation;

import java.io.Serializable;

public class SerProactiveSubtractionEvaluation implements ProactiveSubtractionEvaluation, Serializable {

    private static final long serialVersionUID = -8243167417859486003L;

    // the retroactive condition, narrowed by the proactive one where there is one
    @Override
    public boolean holds (Configuration configuration,
                          LogicCondition proactiveCondition,
                          LogicCondition retroactiveCondition){
        return this.getOverallCondition(proactiveCondition, retroactiveCondition).evaluate(configuration.toAssignment());
    }

    @Override
    public LogicCondition getOverallCondition(LogicCondition proactiveCondition, LogicCondition retroactiveCondition) {
        if (retroactiveCondition == null)
            return LogicCondition.FALSE;
        return LogicCondition.and(retroactiveCondition, proactiveCondition);
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
