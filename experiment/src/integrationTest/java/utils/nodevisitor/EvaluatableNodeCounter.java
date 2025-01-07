package nodevisitor;

import at.jku.isse.ecco.experiment.utils.vevos.GroundTruth;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

import java.nio.file.Path;

public class EvaluatableNodeCounter implements Node.Op.NodeVisitor {

    private final FormulaFactory formulaFactory;
    private final GroundTruth groundTruth;

    private int count;

    public EvaluatableNodeCounter(Path groundTruths) {
        this.formulaFactory = new FormulaFactory();
        this.groundTruth = new GroundTruth(groundTruths);
        this.count = 0;
    }

    @Override
    public void visit(Node.Op node) {
        Location location = node.getLocation();
        if (location == null){ return; }
        Formula groundTruth = this.getGroundTruth(location);

        // ignore "BASE"-ground-truths
        if (!groundTruth.toString().equals("$true")){
            this.count++;
        }
    }

    private Formula getGroundTruth(Location location){
        return this.groundTruth.getCondition(location, this.formulaFactory);
    }

    public int getCount(){
        return this.count;
    }
}
