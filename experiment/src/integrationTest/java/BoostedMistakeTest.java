import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.experiment.config.ExperimentConfiguration;
import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.picker.featuretracepicker.RandomFeatureTracePicker;
import at.jku.isse.ecco.experiment.mistake.FeatureSwitcher;
import at.jku.isse.ecco.experiment.mistake.MistakeCreator;
import at.jku.isse.ecco.experiment.mistake.MistakeStrategy;
import at.jku.isse.ecco.experiment.picker.featuretracepicker.SingleAssociationTracePicker;
import at.jku.isse.ecco.experiment.runner.RepositoryPreparator;
import at.jku.isse.ecco.experiment.sample.VevosFeatureSampler;
import at.jku.isse.ecco.experiment.trainer.EccoRepoTrainer;
import at.jku.isse.ecco.experiment.utils.CounterVisitor;
import at.jku.isse.ecco.experiment.picker.MemoryListPicker;
import at.jku.isse.ecco.experiment.utils.vevos.GroundTruth;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.maintree.MemBoostedAssociationMerger;
import at.jku.isse.ecco.util.directory.DirectoryException;
import at.jku.isse.ecco.util.directory.DirectoryUtils;
import at.jku.isse.ecco.util.resource.ResourceException;
import at.jku.isse.ecco.util.resource.ResourceUtils;
import utils.nodevisitor.EvaluatableNodeCounter;
import utils.nodevisitor.MistakeCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.variantsync.vevos.simulation.io.Resources;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BoostedMistakeTest {

    private final String configPath = ResourceUtils.getResourceFolderPathAsString("configs/boost_mistakes.properties");
    private final Path variantBasePath = ResourceUtils.getResourceFolderPath("sample");
    private final Path repoPath = ResourceUtils.getResourceFolderPath("repo");

    public BoostedMistakeTest() throws ResourceException {
    }


    @BeforeEach
    public void setLoggerLevel(){
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.FINE);
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setLevel(Level.FINE);
        }
    }

    @BeforeEach
    public void deleteResources() throws DirectoryException {
        DirectoryUtils.deleteAndCreateFolder(this.variantBasePath);
        DirectoryUtils.deleteAndCreateFolder(this.repoPath);
    }

    private void analyzeAssociations(Collection<Association.Op> associations, MistakeCreator mistakeCreator){
        for (Association.Op association : associations) {
            CounterVisitor counterVisitor = new CounterVisitor();
            association.getRootNode().traverse(counterVisitor);
            System.out.println("\nnumber of nodes with proactive feature traces in association " + association + ":" + counterVisitor.getUserConditionCount());

            MistakeCounter mistakeCounter = new MistakeCounter(mistakeCreator);
            association.getRootNode().traverse(mistakeCounter);
            System.out.println("number of faulty traces in association " + association + ":" + mistakeCounter.getMistakeCount());

            EvaluatableNodeCounter evaluatableNodeCounter = new EvaluatableNodeCounter(variantBasePath);
            association.getRootNode().traverse(evaluatableNodeCounter);
            System.out.println("number of evaluatable nodes in association " + association + ":" + evaluatableNodeCounter.getCount());
        }
    }

    @Test
    public void boostedMistakesDisableBoosting() throws Resources.ResourceIOException, IOException, ResourceException {
        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(this.configPath, this.variantBasePath);
        ExperimentRunConfiguration runConfig = experimentConfig.getNextRunConfiguration();
        VevosFeatureSampler sampler = new VevosFeatureSampler();
        sampler.sample(runConfig, 50);
        runConfig.pickVariants();
        EccoRepoTrainer trainer = new EccoRepoTrainer(runConfig);
        trainer.train();
        Repository.Op repo = trainer.getRepository();

        MistakeStrategy mistakeStrategy = new FeatureSwitcher(runConfig.getFeatures());
        MistakeCreator mistakeCreator = new MistakeCreator(mistakeStrategy);
        MemoryListPicker<FeatureTrace> listPicker = new RandomFeatureTracePicker();
        RepositoryPreparator repositoryPreparator = new RepositoryPreparator(mistakeCreator, listPicker);
        GroundTruth groundTruth = new GroundTruth(this.variantBasePath);
        repositoryPreparator.prepareRepository(repo, 1, 100, groundTruth);

        Collection<Association.Op> associations = (Collection<Association.Op>) repo.getAssociations();
        this.analyzeAssociations(associations, mistakeCreator);

        repo.setMaintreeBuildingStrategy(new MemBoostedAssociationMerger());
        repo.buildMainTree();
    }

    @Test
    public void nonConflictingMistakesGetBoosted() throws Resources.ResourceIOException, IOException, ResourceException {
        ExperimentConfiguration experimentConfig = new ExperimentConfiguration(this.configPath, this.variantBasePath);
        ExperimentRunConfiguration runConfig = experimentConfig.getNextRunConfiguration();
        VevosFeatureSampler sampler = new VevosFeatureSampler();
        sampler.sample(runConfig, 50);
        runConfig.pickVariants();
        EccoRepoTrainer trainer = new EccoRepoTrainer(runConfig);
        trainer.train();
        Repository.Op repo = trainer.getRepository();

        MistakeStrategy mistakeStrategy = new FeatureSwitcher(runConfig.getFeatures());
        MistakeCreator mistakeCreator = new MistakeCreator(mistakeStrategy);
        MemoryListPicker<FeatureTrace> listPicker = new SingleAssociationTracePicker();
        RepositoryPreparator repositoryPreparator = new RepositoryPreparator(mistakeCreator, listPicker);
        GroundTruth groundTruth = new GroundTruth(this.variantBasePath);
        repositoryPreparator.prepareRepository(repo, 100, 100, groundTruth);

        Collection<Association.Op> associations = (Collection<Association.Op>) repo.getAssociations();
        this.analyzeAssociations(associations, mistakeCreator);

        repo.setMaintreeBuildingStrategy(new MemBoostedAssociationMerger());
        repo.buildMainTree();
    }


}
