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
import at.jku.isse.ecco.featuretrace.evaluation.UserBasedEvaluation;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import config.DummyConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.security.Provider;

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
        dummyConfiguration.setFeatureTracePercentage(100);
        dummyConfiguration.setMistakePercentage(0);
        dummyConfiguration.setEvaluationStrategy(new DiffBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("ConditionSwapper");
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
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentage(0);
        dummyConfiguration.setMistakePercentage(0);
        dummyConfiguration.setEvaluationStrategy(new DiffBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("FeatureSwitcher");
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
        dummyConfiguration.setFeatureTracePercentage(100);
        dummyConfiguration.setMistakePercentage(0);
        dummyConfiguration.setEvaluationStrategy(new UserBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("FeatureSwitcher");
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
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentage(0);
        dummyConfiguration.setMistakePercentage(0);
        dummyConfiguration.setEvaluationStrategy(new DiffBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("FeatureSwitcher");
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
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentage(100);
        dummyConfiguration.setMistakePercentage(0);
        dummyConfiguration.setEvaluationStrategy(new DiffBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("FeatureSwitcher");
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());
        assertEquals(1.0, result.getF1());

        // number of artifacts: 10 + 3 + 3 + 3 + 3 + 3 lines ==> 25 Artifacts (that are not BASE-Artifacts)
        // 2 Features ==> 4 Combinations
        // 25 x 4 = 100 atomic results
        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(100, atomicResults);
        // 6 artifacts per condition
        // A        positive: 2; negative: 2
        // B        positive: 2; negative: 2
        // A || B   positive: 3; negative: 1
        // A && B   positive: 1; negative: 3
        // ~A       positive: 2; negative: 2
        // positive: 60; negative: 60
        assertEquals(50, result.getTp());
        assertEquals(50, result.getTn());
    }


    @Test
    public void featureARepoCreatesCorrectResults() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_2/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(1);
        dummyConfiguration.setFeatureTracePercentage(0);
        dummyConfiguration.setMistakePercentage(0);
        dummyConfiguration.setEvaluationStrategy(new DiffBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("FeatureSwitcher");
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
        // BASE     tp:    fp:    tn:10  fn:
        // A        tp:10  fp:    tn:    fn:
        // B        tp:    fp:    tn:5   fn:5
        // A && B   tp:10  fp:    tn:    fn:
        //          tp:20  fp:    tn:15  fn:5
        assertEquals(20, result.getTp());
        assertEquals(15, result.getTn());
        assertEquals(5, result.getFn());
        assertEquals(1.0, result.getPrecision());
        assertEquals(0.8, result.getRecall());
        assertEquals(0.888888888888889, result.getF1());
    }


    @Test
    public void featureABRepoCreatesCorrectResults() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_3/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(1);
        dummyConfiguration.setFeatureTracePercentage(0);
        dummyConfiguration.setMistakePercentage(0);
        dummyConfiguration.setEvaluationStrategy(new DiffBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("FeatureSwitcher");
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
        // BASE     tp:    fp:    tn:20  fn:
        // A        tp:    fp:    tn:10  fn:10
        // B        tp:    fp:    tn:10  fn:10
        // A && B   tp:20  fp:    tn:    fn:
        //          tp:20  fp:    tn:40  fn:20
        assertEquals(20, result.getTp());
        assertEquals(40, result.getTn());
        assertEquals(20, result.getFn());
        assertEquals(1.0, result.getPrecision());
        assertEquals(0.5, result.getRecall());
        assertEquals(0.6666666666666666, result.getF1());
    }


    @Test
    public void featureBRepoCreatesCorrectResults() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_4/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(1);
        dummyConfiguration.setFeatureTracePercentage(100);
        dummyConfiguration.setMistakePercentage(0);
        dummyConfiguration.setEvaluationStrategy(new DiffBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("FeatureSwitcher");
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

        Repository.Op repo = prepareRepository(config);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunner runner = new CExperimentRunner(config, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        // number of artifacts: 6 + 9 lines ==> 20 Artifacts (that are not BASE-Artifacts) (B, ~A, A||B)
        // 2 Features ==> 4 Combinations
        // 15 x 4 = 60 atomic results
        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(60, atomicResults);
        // BASE     tp:    fp:    tn:10  fn:5
        // A        tp:    fp:    tn:10  fn:5
        // B        tp:15  fp:    tn:    fn:
        // A && B   tp:10  fp:5   tn:    fn:
        //          tp:25  fp:5   tn:20  fn:10
        assertEquals(25, result.getTp());
        assertEquals(5, result.getFp());
        assertEquals(20, result.getTn());
        assertEquals(10, result.getFn());
        assertEquals(0.8333333333333334, result.getPrecision());
        assertEquals(0.7142857142857143, result.getRecall());
        assertEquals(0.7692307692307692, result.getF1());
    }


    @Test
    public void featureBASERepoCreatesCorrectResults() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_5/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(1);
        dummyConfiguration.setFeatureTracePercentage(0);
        dummyConfiguration.setMistakePercentage(0);
        dummyConfiguration.setEvaluationStrategy(new DiffBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("FeatureSwitcher");
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
        // BASE     tp:5   fp:    tn:    fn:
        // A        tp:    fp:5   tn:    fn:
        // B        tp:5   fp:    tn:    fn:
        // A && B   tp:    fp:5   tn:    fn:
        //          tp:10  fp:10  tn:    fn:
        assertEquals(10, result.getTp());
        assertEquals(10, result.getFp());
        assertEquals(0.5, result.getPrecision());
        assertEquals(1.0, result.getRecall());
        assertEquals(0.6666666666666666, result.getF1());
    }


    @Test
    public void allFeatureTracesCreatePerfektScoreDespiteFlawedDiffConditions() {
    DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_5/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(1);
        dummyConfiguration.setFeatureTracePercentage(100);
        dummyConfiguration.setMistakePercentage(0);
        dummyConfiguration.setEvaluationStrategy(new UserBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("FeatureSwitcher");
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
        // BASE     tp:5   fp:    tn:    fn:
        // A        tp:    fp:    tn:5   fn:
        // B        tp:5   fp:    tn:    fn:
        // A && B   tp:    fp:    tn:5   fn:
        //          tp:10  fp:    tn:10  fn:
        assertEquals(10, result.getTp());
        assertEquals(10, result.getTn());
        assertEquals(1.0, result.getPrecision());
        assertEquals(1.0, result.getRecall());
        assertEquals(1.0, result.getF1());
    }

    // making mistakes worsens the result (for every combination of mistake-strategy and user-based evaluation)
    // making mistakes does not worsen the result beyond the affected artifacts
    @Test
    public void mistakesWorsenResultUsingConditionSwapper() {
    DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentage(100);
        dummyConfiguration.setMistakePercentage(50);
        dummyConfiguration.setEvaluationStrategy(new UserBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("ConditionSwapper");
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

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

    @Test
    public void mistakesWorsenResultUsingConjugator() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentage(100);
        dummyConfiguration.setMistakePercentage(50);
        dummyConfiguration.setEvaluationStrategy(new UserBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("Conjugator");
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

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

    @Test
    public void mistakesWorsenResultUsingFeatureSwitcher() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentage(100);
        dummyConfiguration.setMistakePercentage(50);
        dummyConfiguration.setEvaluationStrategy(new UserBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("FeatureSwitcher");
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

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

    @Test
    public void mistakesWorsenResultUsingOperatorSwapper() {
        DummyConfiguration dummyConfiguration = new DummyConfiguration();
        dummyConfiguration.setVariantsDir(ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit"));
        dummyConfiguration.setNumberOfVariants(4);
        dummyConfiguration.setFeatureTracePercentage(100);
        dummyConfiguration.setMistakePercentage(40);
        dummyConfiguration.setEvaluationStrategy(new UserBasedEvaluation());
        dummyConfiguration.setMistakeStrategy("OperatorSwapper");
        ExperimentRunConfiguration config = dummyConfiguration.createRunConfiguration();
        config.pickVariants();

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
