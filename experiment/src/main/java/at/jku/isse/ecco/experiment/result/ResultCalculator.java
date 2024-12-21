package at.jku.isse.ecco.experiment.result;


import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.result.persister.ResultPersister;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.tree.Node;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.FormulaFactory;

import java.util.Collection;
import java.util.stream.Collectors;


public class ResultCalculator {
    private final ResultPersister resultPersister;
    private final ExperimentRunConfiguration config;
    private final int featureTracePercentage;
    private final int mistakePercentage;
    private final String mistakeStrategy;
    private final EvaluationStrategy evaluationStrategy;
    private final boolean boosting;

    public ResultCalculator(ExperimentRunConfiguration config, int featureTracePercentage, ResultPersister resultPersister,
                            EvaluationStrategy evaluationStrategy, int mistakePercentage, String mistakeStrategy, boolean boosting){
        this.config = config;
        this.resultPersister = resultPersister;
        this.featureTracePercentage = featureTracePercentage;
        this.evaluationStrategy = evaluationStrategy;
        this.mistakePercentage = mistakePercentage;
        this.mistakeStrategy = mistakeStrategy;
        this.boosting = boosting;
    }

    public void calculateMetrics(Node.Op mainTree){
        FormulaFactory formulaFactory = new FormulaFactory();
        Collection<Assignment> assignments = AssignmentPowerset.getAssignmentPowerset(formulaFactory, this.config.getFeatures());
        EvaluationVisitor visitor = new EvaluationVisitor(formulaFactory, assignments, this.config.getVariantsDir(), this.evaluationStrategy);
        mainTree.traverse(visitor);
        Collection<NodeResult> nodeResults = visitor.getResults();
        Collection<Result> results = nodeResults.stream().map(NodeResult::getResult).collect(Collectors.toList());
        Result overallResult = Result.overallResult(results);
        this.resultPersister.persist(overallResult, this.config, featureTracePercentage, mistakePercentage, evaluationStrategy, mistakeStrategy, this.boosting);
    }
}
