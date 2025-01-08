package utils.nodevisitor;

import at.jku.isse.ecco.experiment.mistake.MistakeCreator;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.tree.Node;

import java.util.Collection;

public class MistakeCounter implements Node.Op.NodeVisitor {

    private final Collection<FeatureTrace> faultyTraces;
    private int mistakeCount;

    public MistakeCounter(MistakeCreator mistakeCreator){
        this.faultyTraces = mistakeCreator.getFaultyTraces();
    }

    @Override
    public void visit(Node.Op node) {
        FeatureTrace featureTrace = node.getFeatureTrace();
        if (this.faultyTraces.stream().anyMatch(faultyTrace -> faultyTrace == featureTrace)){
            this.mistakeCount++;
        }
    }

    public int getMistakeCount(){
        return this.mistakeCount;
    }
}
