package at.jku.isse.ecco.experiment.utils.tracecollector;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class FeatureTraceCollector implements  Node.Op.NodeVisitor{

    private List<FeatureTrace> featureTraces = new LinkedList<>();

    @Override
    public void visit(Node.Op node) {
        if (!node.isUnique()){ return; }
        if (node.getLocation() == null){ return; }
        FeatureTrace featureTrace = node.getFeatureTrace();
        if (featureTrace == null) { throw new RuntimeException("Node with location has no feature trace."); }
        if (featureTrace.containsUserCondition()) {
            this.featureTraces.add(featureTrace);
        }
    }

    public void collectAssociationTraces(Repository.Op repository){
        Collection<Association.Op> associations = (Collection<Association.Op>) repository.getAssociations();
        for (Association.Op association : associations){
            association.getRootNode().traverse(this);
        }
    }

    public List<FeatureTrace> getFeatureTraces(){
        return this.featureTraces;
    }
}
