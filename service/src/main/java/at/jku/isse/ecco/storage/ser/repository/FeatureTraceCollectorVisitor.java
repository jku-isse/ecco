package at.jku.isse.ecco.storage.ser.repository;

import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.tree.Node;

import java.util.Collection;
import java.util.LinkedList;

public class FeatureTraceCollectorVisitor implements Node.NodeVisitor {

    private Collection<FeatureTrace> featureTraces = new LinkedList<>();

    @Override
    public void visit(Node node) {
        Node.Op nodeOp = (Node.Op) node;
        FeatureTrace featureTrace = nodeOp.getFeatureTrace();
        if (featureTrace == null) { return; }
        if (featureTrace.containsProactiveCondition()) {
            this.featureTraces.add(featureTrace);
        }
    }

    public Collection<FeatureTrace> getFeatureTraces() {
        return this.featureTraces;
    }
}
