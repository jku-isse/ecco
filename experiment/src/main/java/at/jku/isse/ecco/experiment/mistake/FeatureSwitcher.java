package at.jku.isse.ecco.experiment.mistake;

import at.jku.isse.ecco.experiment.utils.CollectionUtils;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import at.jku.isse.ecco.repository.Repository;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class FeatureSwitcher implements MistakeStrategy {

    private final List<String> features;

    private final FormulaFactory formulaFactory = new FormulaFactory();

    public FeatureSwitcher(List<String> features){
        this.features = features;
    }

    @Override
    public void createMistake(FeatureTrace trace){
        try {
            String userConditionString = trace.getUserConditionString();
            Formula userCondition = LogicUtils.parseString(this.formulaFactory, userConditionString);
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
            trace.setUserCondition(userConditionString.replace(oldFeature, randomFeature));
        } catch (Exception e){
            throw new RuntimeException("FeatureSwitcher failed to create mistake.");
        }
    }

    @Override
    public void init(Repository.Op repository) {}

    @Override
    public String toString(){
        return "ConditionSwapper (Features: " + this.features + ")";
    }
}
