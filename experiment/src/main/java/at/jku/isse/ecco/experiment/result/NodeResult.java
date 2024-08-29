package at.jku.isse.ecco.experiment.result;

import at.jku.isse.ecco.tree.Node;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;


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

    public void updateResult(Assignment assignment){
        boolean result = this.resultCondition.evaluate(assignment);
        boolean truth = this.groundTruth.evaluate(assignment);
        if (result && truth){
            this.result.incTP();
        } else if (result && !truth) {
            this.result.incFP();
        } else if (!result && !truth) {
            this.result.incTN();
        } else if (!result && truth) {
            this.result.incFN();
        }
    }

    public Result getResult(){
        return this.result;
    }

    public void computeMetrics(){
        this.result.computeMetrics();
    }
}
