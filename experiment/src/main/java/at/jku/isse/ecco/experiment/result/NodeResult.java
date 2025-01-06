package at.jku.isse.ecco.experiment.result;

import at.jku.isse.ecco.tree.Node;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;

import java.util.Collection;

public class NodeResult {

    private Node.Op node;
    private Formula groundTruth;
    private Formula resultCondition;
    private Result result = new Result();


    public NodeResult(Node.Op node, Formula resultCondition, Formula groundTruth){
        this.node = node;
        this.resultCondition = resultCondition;
        this.groundTruth = groundTruth;
    }

    public void updateResult(Collection<Assignment> assignments){
        this.result.updateResult(this.resultCondition, this.groundTruth, assignments);
    }

    public Result getResult(){
        return this.result;
    }

    public void computeMetrics(){
        this.result.computeMetrics();
    }

    public Node.Op getNode(){
        return this.node;
    }
}
