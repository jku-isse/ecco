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

public class Conjugator implements MistakeStrategy {

    private List<String> features;

    private FormulaFactory formulaFactory = new FormulaFactory();

    public Conjugator(List<String> features){
        this.features = features;
    }

    @Override
    public void createMistake(FeatureTrace trace) {
        try {
            String userConditionString = trace.getUserConditionString();
            Formula userCondition = LogicUtils.parseString(this.formulaFactory, userConditionString);
            Collection<String> variables = userCondition.variables().stream().map(Formula::toString).collect(Collectors.toSet());
            Collection<String> otherFeatures = this.features.stream().filter(f -> !variables.contains(f)).collect(Collectors.toSet());
            if (otherFeatures.size() == 0){
                throw new RuntimeException("There are no features that are not already used in the condition.");
            }
            String randomFeature = CollectionUtils.getRandom(otherFeatures);
            Formula featureFormula = LogicUtils.parseString(this.formulaFactory, randomFeature);
            trace.setUserCondition(this.formulaFactory.and(userCondition, featureFormula).toString());
        } catch (Exception e){
            throw new RuntimeException("Conjugator failed to make mistake.");
        }
    }

    @Override
    public void init(Repository.Op repository) {}

    @Override
    public String toString(){
        return "ConditionSwapper (Features: " + this.features + ")";
    }
}
