package at.jku.isse.ecco.featuretrace;

import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.tree.Node;

/**
 * A FeatureTrace associates an artifact-node with presence conditions.
 * These presence conditions are logical formulas. The logical variables in these formulas represent Features.
 * Given a set of features (a feature-configuration for a product-variant),
 * a presence condition evaluates to true iff the respective artifact must be part of the respective product variant.
 * The string representation of presence conditions must adhere to the ANTLR grammar used be LogicNG:
 * https://github.com/logic-ng/parser/blob/main/src/main/antlr/LogicNGPropositional.g4 !
 */
public interface FeatureTrace extends Persistable {

    boolean holds(Configuration configuration, EvaluationStrategy evaluationStrategy);

    Node getNode();

    void setNode(Node node);

    boolean containsProactiveCondition();
    void setRetroactiveCondition(LogicCondition retroactiveCondition);
    /** The proactive condition, from text written outside ECCO (a trace) - feature names sanitized. */

    void setProactiveCondition(String proactiveConditionString);
    void setProactiveCondition(LogicCondition proactiveCondition);
    void addProactiveCondition(LogicCondition proactiveCondition);

    void removeProactiveCondition();
    void addRetroactiveCondition(LogicCondition retroactiveCondition);

    void buildProactiveConditionConjunction(String newCondition);
    LogicCondition getProactiveCondition();
    LogicCondition getRetroactiveCondition();

    void fuseFeatureTrace(FeatureTrace featureTrace);
    LogicCondition getOverallCondition(EvaluationStrategy evaluationStrategy);

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();
}
