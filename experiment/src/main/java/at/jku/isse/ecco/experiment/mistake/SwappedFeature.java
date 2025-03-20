package at.jku.isse.ecco.experiment.mistake;

import at.jku.isse.ecco.experiment.utils.CollectionUtils;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import org.logicng.formulas.Formula;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class SwappedFeature extends MistakeStrategy {

    private final List<String> features;

    public SwappedFeature(List<String> features){
        this.features = features;
    }

    @Override
    public String createNewMistake(FeatureTrace trace){
        try {
            String userConditionString = trace.getProactiveConditionString();
            Formula userCondition = LogicUtils.parseString(userConditionString);
            Collection<String> variables = userCondition.variables().stream().map(Formula::toString).collect(Collectors.toSet());
            if (variables.isEmpty()){
                throw new RuntimeException("There are no features to switch in the condition.");
            }
            String oldFeature = CollectionUtils.getRandom(variables);
            Collection<String> otherFeatures = this.features.stream().filter(f -> !variables.contains(f)).collect(Collectors.toSet());
            if (otherFeatures.isEmpty()){
                throw new RuntimeException("There are no features that are not already used in the condition.");
            }
            String randomFeature = CollectionUtils.getRandom(otherFeatures);
            String newCondition = userConditionString.replace(oldFeature, randomFeature);
            trace.setProactiveCondition(newCondition);
            return newCondition;
        } catch (Exception e){
            throw new RuntimeException("SwappedFeature failed to create mistake.");
        }
    }

    @Override
    public String toString(){
        return "SwappedFeature (Features: " + this.features + ")";
    }
}
