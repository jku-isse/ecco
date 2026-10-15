package at.jku.isse.ecco.storage.ser.featuretrace.evaluation;

import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.evaluation.ProactiveAdditionEvaluation;

import java.io.Serializable;

public class SerProactiveAdditionEvaluation implements ProactiveAdditionEvaluation, Serializable {

    private static final long serialVersionUID = 5949227201431284966L;

    // either condition (a node without both has one)
    @Override
    public boolean holds (Configuration configuration,
                          LogicCondition proactiveCondition,
                          LogicCondition retroactiveCondition){
        return this.getOverallCondition(proactiveCondition, retroactiveCondition).evaluate(configuration.toAssignment());
    }

    @Override
    public LogicCondition getOverallCondition(LogicCondition proactiveCondition, LogicCondition retroactiveCondition) {
        assert(retroactiveCondition != null || proactiveCondition != null);
        return LogicCondition.or(retroactiveCondition, proactiveCondition);
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
