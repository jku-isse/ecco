package at.jku.isse.ecco.featuretrace;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.tree.Node;

public interface FeatureTrace extends Persistable {

    boolean holds(Configuration configuration, EvaluationStrategy evaluationStrategy);

    Node getNode();

    void setNode(Node node);

    boolean containsProactiveCondition();

    void setRetroactiveCondition(String retroactiveConditionString);

    void setProactiveCondition(String proactiveConditionString);

    void addProactiveCondition(String proactiveCondition);

    void removeProactiveCondition();

    void addRetroactiveCondition(String retroactiveCondition);

    void buildProactiveConditionConjunction(String newCondition);

    String getProactiveConditionString();

    String getRetroactiveConditionString();

    void fuseFeatureTrace(FeatureTrace featureTrace);

    String getOverallConditionString(EvaluationStrategy evaluationStrategy);

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();
}
