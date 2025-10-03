package at.jku.isse.ecco.maintree.building;

import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.tree.Node;

public class BoostVisitor implements Node.Op.NodeVisitor{

    private final String boostCondition;

    public BoostVisitor(String boostCondition){
        this.boostCondition = boostCondition;
    }

    @Override
    public void visit(Node.Op node) {
        FeatureTrace featureTrace = node.getFeatureTrace();
        if (featureTrace != null && node.isUnique()){
            node.getFeatureTrace().addProactiveCondition(boostCondition);
        }
    }
}
