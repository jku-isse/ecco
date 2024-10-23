package at.jku.isse.ecco.experiment.result;


import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.result.persister.ResultPersister;
import at.jku.isse.ecco.tree.Node;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.FormulaFactory;

import java.util.Collection;
import java.util.stream.Collectors;


public class ResultCalculator {
    private final ResultPersister resultPersister;
    private final ExperimentRunConfiguration config;

    public ResultCalculator(ExperimentRunConfiguration config, ResultPersister resultPersister){
        this.config = config;
        this.resultPersister = resultPersister;
    }

    public void calculateMetrics(Node.Op mainTree){
        FormulaFactory formulaFactory = new FormulaFactory();
        Collection<Assignment> assignments = AssignmentPowerset.getAssignmentPowerset(formulaFactory, this.config.getFeatures());
        EvaluationVisitor visitor = new EvaluationVisitor(formulaFactory, assignments, this.config.getVariantsDir(), this.config.getEvaluationStrategy());
        mainTree.traverse(visitor);
        Collection<NodeResult> nodeResults = visitor.getResults();
        Collection<Result> results = nodeResults.stream().map(NodeResult::getResult).collect(Collectors.toList());
        Result overallResult = Result.overallResult(results);
        this.resultPersister.persist(overallResult, this.config);
    }
}
