package at.jku.isse.ecco.featuretrace.evaluation;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Factory that provides instances for different evaluation strategies of feature traces.
 */
public class EvaluationStrategyFactory {
    // inject fields with storage plugin modules

    @Inject
    @Named(ProactiveBasedEvaluation.STRATEGY_NAME)
    private EvaluationStrategy proactiveStrategy;

    @Inject
    @Named(RetroactiveBasedEvaluation.STRATEGY_NAME)
    private EvaluationStrategy retroactiveStrategy;

    @Inject
    @Named(ProactiveAdditionEvaluation.STRATEGY_NAME)
    private EvaluationStrategy proactiveAdditionStrategy;

    @Inject
    @Named(ProactiveSubtractionEvaluation.STRATEGY_NAME)
    private EvaluationStrategy proactiveSubtractionStrategy;

    public EvaluationStrategy getEvaluationStrategy(String strategyName){
        assert(strategyName != null);

        return switch (strategyName) {
            case ProactiveBasedEvaluation.STRATEGY_NAME -> this.getProactiveStrategy();
            case RetroactiveBasedEvaluation.STRATEGY_NAME -> this.getRetroactiveStrategy();
            case ProactiveAdditionEvaluation.STRATEGY_NAME -> this.getProactiveAdditionStrategy();
            case ProactiveSubtractionEvaluation.STRATEGY_NAME -> this.getProactiveSubtractionStrategy();
            default -> throw new IllegalStateException("Given name of evaluation strategy does not exist: " + strategyName);
        };
    }

    private EvaluationStrategy getProactiveStrategy(){
        return this.proactiveStrategy;
    }

    private EvaluationStrategy getRetroactiveStrategy() {
        return retroactiveStrategy;
    }

    private EvaluationStrategy getProactiveAdditionStrategy() {
        return proactiveAdditionStrategy;
    }

    private EvaluationStrategy getProactiveSubtractionStrategy() {
        return proactiveSubtractionStrategy;
    }
}
