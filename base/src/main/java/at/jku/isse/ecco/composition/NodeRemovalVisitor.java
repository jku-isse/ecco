package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.tree.Node;

/**
 * This visitor removes tree-branches containing only nodes that are not applicable
 * according to configuration, evaluation strategy and feature traces of nodes.
 * In order to do so, the tree has to be traversed using this visitor.
 */
public class NodeRemovalVisitor implements Node.Op.DfNodeVisitor{

    private final Configuration configuration;
    private final EvaluationStrategy evaluationStrategy;

    public NodeRemovalVisitor(Configuration configuration, EvaluationStrategy evaluationStrategy) {
        this.configuration = configuration;
        this.evaluationStrategy = evaluationStrategy;
    }

    @Override
    public void dfVisit(Node.Op node) {
        FeatureTrace featureTrace = node.getFeatureTrace();
        // not unique means it was only part of a skeleton in a tree
        // if there is no artifact or no feature trace the node represents a plugin or a folder
        if (!node.isUnique()
                || node.getArtifact() == null
                || featureTrace == null
                || !node.getFeatureTrace().holds(this.configuration, this.evaluationStrategy)){
            if (node.getChildren() == null || node.getChildren().isEmpty()){
                node.removeParent();
            }
        }
    }
}
