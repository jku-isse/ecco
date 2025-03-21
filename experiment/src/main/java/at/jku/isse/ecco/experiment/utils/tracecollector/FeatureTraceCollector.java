package at.jku.isse.ecco.experiment.utils.tracecollector;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.experiment.utils.vevos.GroundTruth;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;
import org.logicng.formulas.Formula;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class FeatureTraceCollector implements Node.Op.NodeVisitor {

    private final List<FeatureTrace> featureTraces = new LinkedList<>();
    private final List<FeatureTrace> evaluableTraces = new LinkedList<>();
    private final GroundTruth groundTruth;

    public FeatureTraceCollector(Repository.Op repository, GroundTruth groundTruth){
        this.groundTruth = groundTruth;
        this.collectAssociationTraces(repository);
    }

    @Override
    public void visit(Node.Op node) {
        if (this.nodeIsValid(node)) {
            this.featureTraces.add(node.getFeatureTrace());
        } else {
            return;
        }

        if (this.nodeIsEvaluable(node)){
            this.evaluableTraces.add(node.getFeatureTrace());
        }
    }

    private boolean nodeIsValid(Node.Op node){
        if (!node.isUnique()){ return false; }
        FeatureTrace featureTrace = node.getFeatureTrace();
        if (featureTrace == null) { return false; }
        if (!featureTrace.containsProactiveCondition()) {return false;}
        return true;
    }

    private boolean nodeIsEvaluable(Node.Op node){
        Optional<Location> optionalLocation = node.getProperty("Location");
        if (optionalLocation.isEmpty()){ return false; }
        Location location = optionalLocation.get();
        Formula groundTruthFormula = this.groundTruth.getCondition(location);
        if (groundTruthFormula.toString().equals("$true")){ return false; }
        return true;
    }

    private void collectAssociationTraces(Repository.Op repository){
        Collection<Association.Op> associations = (Collection<Association.Op>) repository.getAssociations();
        for (Association.Op association : associations){
            association.getRootNode().traverse(this);
        }
    }

    public List<FeatureTrace> getFeatureTraces(){
        return this.featureTraces;
    }

    public List<FeatureTrace> getEvaluableTraces(){
        return this.evaluableTraces;
    }

    public List<FeatureTrace> getNonEvaluableTraces(){
        return this.featureTraces.stream().filter(trace -> !this.evaluableTraces.contains(trace)).toList();
    }
}
