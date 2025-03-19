package mistake.stubs;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.tree.Node;


public class FeatureTraceStub implements FeatureTrace{

    private String proactiveCondition;

    public FeatureTraceStub(String proactiveCondition){
        this.proactiveCondition = proactiveCondition;
    }

    @Override
    public boolean holds(Configuration configuration, EvaluationStrategy evaluationStrategy) { return false; }

    @Override
    public Node getNode() { return null; }

    @Override
    public void setNode(Node node) {}

    @Override
    public boolean containsProactiveCondition() {
        return this.proactiveCondition != null;
    }

    @Override
    public void setRetroactiveCondition(String retroactiveConditionString) {}

    @Override
    public void setProactiveCondition(String proactiveConditionString) {
        this.proactiveCondition = proactiveConditionString;
    }

    @Override
    public void addProactiveCondition(String proactiveCondition) {
        this.proactiveCondition = this.proactiveCondition + proactiveCondition;
    }

    @Override
    public void removeProactiveCondition() {
        this.proactiveCondition = null;
    }

    @Override
    public void addRetroactiveCondition(String retroactiveCondition) {}

    @Override
    public void buildProactiveConditionConjunction(String newCondition) {
        this.proactiveCondition = this.proactiveCondition + " && " + newCondition;
    }

    @Override
    public String getProactiveConditionString() {
        return this.proactiveCondition;
    }

    @Override
    public String getRetroactiveConditionString() { return null; }

    @Override
    public void fuseFeatureTrace(FeatureTrace featureTrace) {}

    @Override
    public String getOverallConditionString(EvaluationStrategy evaluationStrategy) {return null;}
}