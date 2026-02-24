package at.jku.isse.ecco.maintree.factory;

import at.jku.isse.ecco.maintree.building.AssociationMerger;
import at.jku.isse.ecco.maintree.building.BoostedAssociationMerger;
import at.jku.isse.ecco.maintree.building.MainTreeBuildingStrategy;
import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Factory that provides instances for different building strategies for the main tree (like boosted or not boosted).
 */
public class MainTreeBuildingStrategyFactory {

    @Inject
    @Named(BoostedAssociationMerger.STRATEGY_NAME)
    private MainTreeBuildingStrategy boostStrategy;

    @Inject
    @Named(AssociationMerger.STRATEGY_NAME)
    private MainTreeBuildingStrategy noBoostStrategy;

    public MainTreeBuildingStrategy getMainTreeBuildingStrategy(String strategyName){
        assert(strategyName != null);

        return switch (strategyName) {
            case BoostedAssociationMerger.STRATEGY_NAME -> this.getBoostStrategy();
            case AssociationMerger.STRATEGY_NAME -> this.getNoBoostStrategy();
            default -> throw new IllegalStateException(String.format("Strategy name for building strategy of main tree does not exist: %s", strategyName));
        };
    }

    private MainTreeBuildingStrategy getBoostStrategy(){
        return this.boostStrategy;
    }

    private MainTreeBuildingStrategy getNoBoostStrategy(){
        return this.noBoostStrategy;
    }
}
