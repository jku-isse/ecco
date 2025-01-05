package at.jku.isse.ecco.experiment.utils.tracecollector;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.experiment.utils.vevos.GroundTruth;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class FeatureTraceCollector implements  Node.Op.NodeVisitor{

    private List<FeatureTrace> featureTraces = new LinkedList<>();
    private GroundTruth groundTruth;
    private FormulaFactory formulaFactory;

    public FeatureTraceCollector(Repository.Op repository, GroundTruth groundTruth){
        this.collectAssociationTraces(repository);
        this.groundTruth = groundTruth;
        this.formulaFactory = new FormulaFactory();
    }

    @Override
    public void visit(Node.Op node) {
        if (!node.isUnique()){ return; }
        Location location = node.getLocation();
        if (location == null){ return; }

        // handle corner case of ECCO switching up equal nodes with different traces
        Formula groundTruthFormula = this.groundTruth.getCondition(location, this.formulaFactory);
        if (groundTruthFormula.toString().equals("$true")){ return; }

        FeatureTrace featureTrace = node.getFeatureTrace();
        if (featureTrace == null) { throw new RuntimeException("Node with location has no feature trace."); }
        if (featureTrace.containsUserCondition()) {
            this.featureTraces.add(featureTrace);
        }
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
}
