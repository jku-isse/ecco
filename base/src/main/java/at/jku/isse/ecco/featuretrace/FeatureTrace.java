package at.jku.isse.ecco.featuretrace;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.tree.Node;

public interface FeatureTrace extends Persistable {

    boolean holds(Configuration configuration, EvaluationStrategy evaluationStrategy);

    Node getNode();

    void setNode(Node node);

    boolean containsUserCondition();

    void setDiffCondition(String diffConditionString);

    void setUserCondition(String userConditionString);

    void addUserCondition(String userCondition);

    void addDiffCondition(String diffCondition);

    void buildUserConditionConjunction(String newCondition);

    String getUserConditionString();

    String getDiffConditionString();

    void fuseFeatureTrace(FeatureTrace featureTrace);

    String getOverallConditionString(EvaluationStrategy evaluationStrategy);

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();
}
