import at.jku.isse.ecco.experiment.result.Result;
import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.result.persister.ResultDatabasePersister;
import at.jku.isse.ecco.experiment.result.persister.ResultInMemoryPersister;
import at.jku.isse.ecco.experiment.runner.CExperimentRunner;
import at.jku.isse.ecco.experiment.runner.ExperimentRunner;
import at.jku.isse.ecco.experiment.trainer.EccoCRepoTrainer;
import at.jku.isse.ecco.experiment.trainer.EccoTrainer;
import at.jku.isse.ecco.experiment.utils.DirUtils;
import at.jku.isse.ecco.experiment.utils.ResourceUtils;
import at.jku.isse.ecco.experiment.utils.ServiceUtils;
import at.jku.isse.ecco.featuretrace.evaluation.DiffBasedEvaluation;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.featuretrace.evaluation.UserBasedEvaluation;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import config.DummyConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.security.Provider;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CExperimentRunnerTest {

    @AfterEach
    public void deleteRepo(){
        Path repoPath = ResourceUtils.getResourceFolderPath("repo");
        DirUtils.deleteDir(repoPath);
        DirUtils.createDir(repoPath);
    }

    private static Repository.Op prepareRepository(ExperimentRunConfiguration config){
        EccoTrainer trainer = new EccoCRepoTrainer(config);
        trainer.train();
        return trainer.getRepository();
    }

    @Test
    public void experimentRunsWithoutException() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{100});
        dummyConfiguration.setMistakePercentages(new Integer[]{0});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new DiffBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"ConditionSwapper"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        assertFalse(persister.getResults().isEmpty());
    }

    @Test
    public void perfectScoreWhenAllVariantsAreCommitted() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_6/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{0});
        dummyConfiguration.setMistakePercentages(new Integer[]{0});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new DiffBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"FeatureSwitcher"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());
        assertEquals(1.0, result.getF1());
    }


    @Test
    public void using100PercentFeatureTracesResultsInPerfectScore() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{100});
        dummyConfiguration.setMistakePercentages(new Integer[]{0});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new UserBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"FeatureSwitcher"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());
        assertEquals(1.0, result.getF1());
    }


    @Test
    public void mistakesDontChangeResultForZeroPercentMistakes() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_6/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{0});
        dummyConfiguration.setMistakePercentages(new Integer[]{0});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new DiffBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"FeatureSwitcher"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());
        assertEquals(1.0, result.getF1());
    }


    @Test
    public void experimentCreatesTheCorrectNumberOfAtomicResults() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_6/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{100});
        dummyConfiguration.setMistakePercentages(new Integer[]{0});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new DiffBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"FeatureSwitcher"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());
        assertEquals(1.0, result.getF1());

        // number of artifacts: 10 lines
        // 2 Features ==> 4 Combinations
        // 10 x 4 = 40 atomic results
        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(40, atomicResults);
        assertEquals(20, result.getTp());
        assertEquals(20, result.getTn());
    }


    @Test
    public void featureARepoCreatesCorrectResults() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_2/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(1);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{0});
        dummyConfiguration.setMistakePercentages(new Integer[]{0});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new DiffBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"FeatureSwitcher"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        // number of artifacts: 4 + 6 lines ==> 10 Artifacts (that are not BASE-Artifacts)
        // 2 Features ==> 4 Combinations
        // 10 x 4 = 40 atomic results
        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(40, atomicResults);
        // one variant with the features BASE and A will be committed.
        // all artifacts will therefore have the condition "A || BASE".

        // BASE             tp:    fp:10  tn:    fn:
        // BASE && A        tp:10  fp:    tn:    fn:
        // BASE && B        tp:5   fp:5   tn:    fn:
        // BASE && A && B   tp:10  fp:    tn:    fn:
        //                  tp:25  fp:15  tn:0   fn:0
        assertEquals(25, result.getTp());
        assertEquals(15, result.getFp());
        assertEquals(0, result.getTn());
        assertEquals(0, result.getFn());
    }

    @Test
    public void featureARepoCreatesCorrectForMultipleFtPercentagesResults() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_2/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(1);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{0, 100});
        dummyConfiguration.setMistakePercentages(new Integer[]{0});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new UserBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"FeatureSwitcher"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        assertEquals(2, persister.getResults().size());

        Iterator<Result> iterator = persister.getResults().iterator();
        Result result= iterator.next();
        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(40, atomicResults);
        assertEquals(25, result.getTp());
        assertEquals(15, result.getFp());
        assertEquals(0, result.getTn());
        assertEquals(0, result.getFn());

        result= iterator.next();
        atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(40, atomicResults);
        assertEquals(25, result.getTp());
        assertEquals(0, result.getFp());
        assertEquals(15, result.getTn());
        assertEquals(0, result.getFn());
    }


    @Test
    public void featureABRepoCreatesCorrectResults() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_3/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(1);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{0});
        dummyConfiguration.setMistakePercentages(new Integer[]{0});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new DiffBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"FeatureSwitcher"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        // number of artifacts: 8 + 12 lines ==> 20 Artifacts (that are not BASE-Artifacts)
        // 2 Features ==> 4 Combinations
        // 20 x 4 = 80 atomic results
        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(80, atomicResults);

        // BASE             tp:    fp:20  tn:    fn:
        // BASE && A        tp:10  fp:10  tn:    fn:
        // BASE && B        tp:10  fp:10  tn:    fn:
        // BASE && A && B   tp:20  fp:    tn:    fn:
        //                  tp:40  fp:40  tn:0   fn:0
        assertEquals(40, result.getTp());
        assertEquals(40, result.getFp());
        assertEquals(0, result.getTn());
        assertEquals(0, result.getFn());
    }


    @Test
    public void featureBRepoCreatesCorrectResults() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_4/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(1);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{100});
        dummyConfiguration.setMistakePercentages(new Integer[]{0});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new DiffBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"FeatureSwitcher"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        // number of artifacts: 6 + 9 lines ==> 15 Artifacts (that are not BASE-Artifacts) (B, ~A, A||B)
        // 2 Features ==> 4 Combinations
        // 15 x 4 = 60 atomic results
        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(60, atomicResults);

        // BASE             tp:5   fp:10  tn:    fn:
        // BASE && A        tp:5   fp:10  tn:    fn:
        // BASE && B        tp:15  fp:    tn:    fn:
        // BASE && A && B   tp:10  fp:5   tn:    fn:
        //                  tp:35  fp:25  tn:0   fn:0
        assertEquals(35, result.getTp());
        assertEquals(25, result.getFp());
        assertEquals(0, result.getTn());
        assertEquals(0, result.getFn());
    }


    @Test
    public void featureBASERepoCreatesCorrectResults() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_5/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(1);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{0});
        dummyConfiguration.setMistakePercentages(new Integer[]{0});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new DiffBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"FeatureSwitcher"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        // number of artifacts: 5 Artifacts (that are not BASE-Artifacts) (~A)
        // 2 Features ==> 4 Combinations
        // 5 x 4 = 20 atomic results
        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(20, atomicResults);

        // BASE             tp:5   fp:    tn:    fn:
        // BASE && A        tp:    fp:5   tn:    fn:
        // BASE && B        tp:5   fp:    tn:    fn:
        // BASE && A && B   tp:    fp:5   tn:    fn:
        //                  tp:10  fp:10  tn:0   fn:0
        assertEquals(10, result.getTp());
        assertEquals(10, result.getFp());
        assertEquals(0, result.getTn());
        assertEquals(0, result.getFn());
    }


    @Test
    public void allFeatureTracesCreatePerfektScoreDespiteFlawedDiffConditions() {
    DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_5/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(1);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{100});
        dummyConfiguration.setMistakePercentages(new Integer[]{0});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new UserBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"FeatureSwitcher"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        // number of artifacts: 5 Artifacts (that are not BASE-Artifacts) (~A)
        // 2 Features ==> 4 Combinations
        // 5 x 4 = 20 atomic results
        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(20, atomicResults);
        assertEquals(10, result.getTp());
        assertEquals(10, result.getTn());
    }

    // making mistakes worsens the result (for every combination of mistake-strategy and user-based evaluation)
    // making mistakes does not worsen the result beyond the affected artifacts
    @Test
    public void mistakesWorsenResultUsingConditionSwapper() {
    DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{100});
        dummyConfiguration.setMistakePercentages(new Integer[]{50});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new UserBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"ConditionSwapper"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(100, atomicResults);
        assertTrue(result.getF1() < 1.0);
    }

    @Test
    public void mistakesWorsenResultUsingConjugator() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_6/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{100});
        dummyConfiguration.setMistakePercentages(new Integer[]{50});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new UserBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"Conjugator"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(40, atomicResults);
        assertTrue(result.getF1() < 1.0);
    }

    @Test
    public void mistakesWorsenResultUsingFeatureSwitcher() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_6/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{100});
        dummyConfiguration.setMistakePercentages(new Integer[]{50});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new UserBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"FeatureSwitcher"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(40, atomicResults);
        assertTrue(result.getF1() < 1.0);
    }

    @Test
    public void mistakesWorsenResultUsingOperatorSwapper() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentages(new Integer[]{100});
        dummyConfiguration.setMistakePercentages(new Integer[]{40});
        List<EvaluationStrategy> evalStrategies = new LinkedList<>();
        evalStrategies.add(new UserBasedEvaluation());
        dummyConfiguration.setEvaluationStrategies(evalStrategies);
        dummyConfiguration.setMistakeStrategies(new String[]{"OperatorSwapper"});
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(100, atomicResults);
        assertTrue(result.getF1() < 1.0);
    }

    /*
    @Test
    public void changingArtifactOrderWorks() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_Order_SPL/Order_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(2);
        dummyConfiguration.setFeatureTracePercentage(100);
        dummyConfiguration.setMistakePercentage(0);
        dummyConfiguration.setEvaluationStrategy(new DiffBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("OperatorSwapper");
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        // number of artifacts: 25 Artifacts (that are not BASE-Artifacts) (~A)
        // 2 Features ==> 4 Combinations
        // 25 x 4 = 100 atomic results
        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(100, atomicResults);
        assertTrue(result.getF1() < 1.0);
    }
     */
}
