package mistake;

import at.jku.isse.ecco.experiment.result.Result;
import org.logicng.formulas.Formula;

public class ConditionSwapAnalysis {

    private final Formula formula;
    private final Formula groundTruth;
    private final Result result;

    public ConditionSwapAnalysis(Formula formula, Formula groundTruth, Result result){
        this.formula = formula;
        this.groundTruth = groundTruth;
        this.result = result;
    }

    public void printAnalysis(){
        System.out.println("Formula: " + formula);
        System.out.println("Ground Truth: " + groundTruth);
        System.out.println("Precision: " + result.getPrecision());
        System.out.println("Recall: " + result.getRecall());
        System.out.println("F1: " + result.getF1());
    }

    public Result getResult(){
        return this.result;
    }

}
