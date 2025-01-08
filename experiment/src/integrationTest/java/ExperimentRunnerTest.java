import at.jku.isse.ecco.experiment.config.Boosting;
import at.jku.isse.ecco.experiment.picker.featuretracepicker.RandomFeatureTracePicker;
import at.jku.isse.ecco.experiment.result.Result;
import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;

import at.jku.isse.ecco.experiment.result.persister.ResultInMemoryPersister;
import at.jku.isse.ecco.experiment.runner.ExperimentRunner;
import at.jku.isse.ecco.experiment.runner.ExperimentRunnerInterface;
import at.jku.isse.ecco.experiment.trainer.EccoRepoTrainer;
import at.jku.isse.ecco.experiment.trainer.EccoTrainerInterface;
import at.jku.isse.ecco.experiment.utils.DirUtils;
import at.jku.isse.ecco.experiment.utils.ResourceUtils;
import at.jku.isse.ecco.featuretrace.evaluation.DiffBasedEvaluation;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.featuretrace.evaluation.UserBasedEvaluation;
import at.jku.isse.ecco.repository.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class ExperimentRunnerTest {

    @Mock
    ExperimentRunConfiguration runConfig;


    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(runConfig.getMinVariantFeatures()).thenReturn(0);
        when(runConfig.getMaxVariantFeatures()).thenReturn(10);
        when(runConfig.getNumberOfRuns()).thenReturn(1);
        when(runConfig.getRepositoryName()).thenReturn("");
    }

    @AfterEach
    public void deleteRepo(){
        Path repoPath = ResourceUtils.getResourceFolderPath("repo");
        DirUtils.deleteDir(repoPath);
        DirUtils.createDir(repoPath);
    }

    private static Repository.Op prepareRepository(ExperimentRunConfiguration config){
        EccoTrainerInterface trainer = new EccoRepoTrainer(config);
        trainer.train();
        return trainer.getRepository();
    }

    @Test
    public void experimentRunsWithoutException() {
        // mock run config
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_1");
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);
        
        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();
        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();
        assertFalse(persister.getResults().isEmpty());
    }

    @Test
    public void mistakesCanBeBoosted() {
        // mock run config
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_7");
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{10});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();
        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();
        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());
        assertEquals(0.75, result.getF1());
    }

    @Test
    public void experimentWithBoostRunsWithoutException(){
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_1");
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);
        
        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();
        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();
        assertFalse(persister.getResults().isEmpty());
    }

    @Test
    public void perfectScoreWhenAllVariantsAreCommitted() {
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_6");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new DiffBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());
        assertEquals(1.0, result.getF1());
    }

    @Test
    public void perfectScoreWithBoostWhenAllVariantsAreCommitted() {
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_6");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new DiffBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());
        assertEquals(1.0, result.getF1());
    }

    @Test
    public void using100PercentFeatureTracesResultsInPerfectScore() {
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_1");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());
        assertEquals(1.0, result.getF1());
    }

    @Test
    public void using100PercentFeatureTracesAndBoostResultsInPerfectScore() {
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_1");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());
        assertEquals(1.0, result.getF1());
    }

    @Test
    public void mistakesDontChangeResultForZeroPercentMistakes() {
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_6");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new DiffBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());
        assertEquals(1.0, result.getF1());
    }

    @Test
    public void mistakesDontChangeResultForZeroPercentMistakesAndBoost() {
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_6");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new DiffBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());
        assertEquals(1.0, result.getF1());
    }


    @Test
    public void experimentCreatesTheCorrectNumberOfAtomicResults() {
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_6");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new DiffBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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
    public void experimentCreatesTheCorrectNumberOfAtomicResultsWithBoost() {
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_6");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new DiffBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_2");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new DiffBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE, FEATUREA"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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
    public void featureARepoCreatesCorrectResultsWithBoost() {
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_2");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new DiffBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE, FEATUREA"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{0, 100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_2");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE, FEATUREA"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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
    public void featureARepoCreatesCorrectForMultipleFtPercentagesResultsWithBoost() {
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{0, 100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_2");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE, FEATUREA"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_3");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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
    public void featureABRepoCreatesCorrectResultsWithBoost() {
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_3");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_4");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new DiffBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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
    public void featureBRepoCreatesCorrectResultsWithBoost() {
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_4");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new DiffBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_5");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new DiffBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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
    public void featureBASERepoCreatesCorrectResultsWithBoost() {
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_5");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new DiffBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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
    public void allFeatureTracesCreatePerfectScoreDespiteFlawedDiffConditions() {
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_5");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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

    @Test
    public void allFeatureTracesAndBoostCreatePerfectScoreDespiteFlawedDiffConditions() {
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_5");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
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

    @Test
    public void mistakesWorsenResultUsingConditionSwapper() {
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_1");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{50});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"ConditionSwapper"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(100, atomicResults);
        assertTrue(result.getF1() < 1.0);
    }

    @Test
    public void mistakesWorsenResultUsingConjugator() {
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_6");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{50});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"Conjugator"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(40, atomicResults);
        assertTrue(result.getF1() < 1.0);
    }

    @Test
    public void mistakesWorsenResultUsingFeatureSwitcher() {
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_6");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{50});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(40, atomicResults);
        assertTrue(result.getF1() < 1.0);
    }

    @Test
    public void mistakesWorsenResultUsingOperatorSwapper() {
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_1");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{40});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"OperatorSwapper"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        assertEquals(1, persister.getResults().size());

        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(100, atomicResults);
        assertTrue(result.getF1() < 1.0);
    }

    @Test
    public void mistakesMustNotPersist(){
        when(runConfig.getBoosting()).thenReturn(Boosting.DISABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_6");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{100, 0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Collection<Result> results= persister.getResults();
        Iterator<Result> resultIterator = results.iterator();
        assertTrue(resultIterator.next().getF1() < 1.0);
        assertEquals(1.0, resultIterator.next().getF1());
    }

    @Test
    public void mistakesMustNotPersistWithBoost(){
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{100});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_6");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{100, 0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE", "BASE, FEATUREA", "BASE, FEATUREB", "BASE, FEATUREA, FEATUREB"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Collection<Result> results= persister.getResults();
        Iterator<Result> resultIterator = results.iterator();
        assertTrue(resultIterator.next().getF1() < 1.0);
        assertEquals(1.0, resultIterator.next().getF1());
    }

    @Test
    public void boostingCreatesPerfectScoreTest(){
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{50});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_5");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result= persister.getResults().iterator().next();
        int atomicResults = result.getFn() + result.getFp() + result.getTp() + result.getTn();
        assertEquals(20, atomicResults);
        assertEquals(10, result.getTp());
        assertEquals(10, result.getTn());
    }

    @Test
    public void boostingDoesNotHappenForContradictingTraces(){
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{50});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_2");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{0});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result = persister.getResults().iterator().next();
        assertTrue(result.getF1() < 1.0);
    }

    @Test
    public void boostingDoesNotHappenBecauseOfMistake(){
        when(runConfig.getBoosting()).thenReturn(Boosting.ENABLED);
        when(runConfig.getFeatureTracePercentages()).thenReturn(new Integer[]{40});
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_5");
        when(runConfig.getVariantsDir()).thenReturn(variantBasePath);
        EvaluationStrategy evaluationStrategy = new UserBasedEvaluation();
        when(runConfig.getEvaluationStrategies()).thenReturn(List.of(evaluationStrategy));
        when(runConfig.getFeaturesIncludingBase()).thenReturn(List.of("FEATUREA", "FEATUREB", "BASE"));
        when(runConfig.getMistakePercentages()).thenReturn(new Integer[]{50});
        when(runConfig.getMistakeStrategies()).thenReturn(new String[]{"FeatureSwitcher"});
        when(runConfig.getVariantConfigurations()).thenReturn(List.of("BASE"));
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);

        Repository.Op repo = prepareRepository(runConfig);
        ResultInMemoryPersister persister = new ResultInMemoryPersister();

        ExperimentRunnerInterface runner = new ExperimentRunner(runConfig, repo, persister);
        runner.runExperiment();

        Result result = persister.getResults().iterator().next();
        assertTrue(result.getF1() < 1.0);
    }
}

