package at.jku.isse.ecco.experiment.result;

import at.jku.isse.ecco.featuretrace.LogicUtils;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import at.jku.isse.ecco.experiment.utils.vevos.GroundTruth;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;


public class EvaluationVisitor implements Node.Op.NodeVisitor {

    private final FormulaFactory formulaFactory;
    private Collection<NodeResult> results = new LinkedList<>();
    private final Collection<Assignment> assignments;
    private final EvaluationStrategy evaluationStrategy;
    private final GroundTruth groundTruth;

    public EvaluationVisitor(FormulaFactory formulaFactory,
                             Collection<Assignment> assignments,
                             Path groundTruths,
                             EvaluationStrategy evaluationStrategy) {
        this.formulaFactory = formulaFactory;
        this.assignments = assignments;
        this.evaluationStrategy = evaluationStrategy;
        this.groundTruth = new GroundTruth(groundTruths);
    }

    @Override
    public void visit(Node.Op node) {
        Location location = node.getLocation();
        // ignore nodes without line numbers (like plugin-node, directories etc.)
        if (location == null){ return; }
        Formula groundTruth = this.getGroundTruth(location);

        // ignore "BASE"-ground-truths
        if (groundTruth.toString().equals("$true")){ return; }

        String resultConditionString = node.getFeatureTrace().getOverallConditionString(this.evaluationStrategy);
        Formula resultCondition = LogicUtils.parseString(this.formulaFactory, resultConditionString);
        this.createResult(node, resultCondition, groundTruth);
    }

    private Formula getGroundTruth(Location location){
        return this.groundTruth.getCondition(location, this.formulaFactory);
    }

    private void createResult(Node.Op node, Formula resultCondition, Formula groundTruth){
        NodeResult nodeResult = new NodeResult(node, resultCondition, groundTruth);
        this.assignments.forEach(nodeResult::updateResult);
        this.results.add(nodeResult);
    }

    public Collection<NodeResult> getResults(){
        return this.results;
    }
}
