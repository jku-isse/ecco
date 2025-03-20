import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.mistake.*;
import at.jku.isse.ecco.experiment.picker.MemoryListPicker;
import at.jku.isse.ecco.experiment.picker.featuretracepicker.RandomFeatureTracePicker;
import at.jku.isse.ecco.experiment.runner.RepositoryPreparator;
import at.jku.isse.ecco.experiment.trainer.EccoRepoTrainer;
import at.jku.isse.ecco.experiment.trainer.EccoTrainerInterface;
import at.jku.isse.ecco.experiment.utils.vevos.GroundTruth;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.featuretrace.MemFeatureTrace;
import at.jku.isse.ecco.util.directory.DirectoryException;
import at.jku.isse.ecco.util.directory.DirectoryUtils;
import at.jku.isse.ecco.util.resource.ResourceException;
import at.jku.isse.ecco.util.resource.ResourceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class RepositoryPreparatorTest {

    @Mock
    ExperimentRunConfiguration runConfig;

    private Repository.Op repository;
    private GroundTruth groundTruth;

    // to check identity
    private Collection<FeatureTrace> repoTraces;
    // to check equality
    private Collection<FeatureTrace> repoTraceCopies;


    @BeforeEach
    public void setup() throws ResourceException {
        MockitoAnnotations.initMocks(this);
        Path variantBasePath = ResourceUtils.getResourceFolderPath("Sampling_Base_1");
        List<Path> variantPicks = new LinkedList<>();
        variantPicks.add(variantBasePath.resolve("Variant_A"));
        variantPicks.add(variantBasePath.resolve("Variant_AB"));
        variantPicks.add(variantBasePath.resolve("Variant_B"));
        variantPicks.add(variantBasePath.resolve("Variant_Null"));
        when(runConfig.getVariantPicks()).thenReturn(variantPicks);
        when(runConfig.getFeatures()).thenReturn(List.of("FEATUREA", "FEATUREB"));

        EccoTrainerInterface trainer = new EccoRepoTrainer(runConfig);
        trainer.train();
        this.repository = trainer.getRepository();
        this.groundTruth = new GroundTruth(variantBasePath);
        this.repoTraces = this.repository.getFeatureTraces();

        this.repoTraceCopies = new ArrayList<>();
        for (FeatureTrace featureTrace : this.repoTraces){
            FeatureTrace newTrace = new MemFeatureTrace(featureTrace.getNode());
            newTrace.setRetroactiveCondition(featureTrace.getRetroactiveConditionString());
            newTrace.setProactiveCondition(featureTrace.getProactiveConditionString());
            this.repoTraceCopies.add(newTrace);
        }
    }

    @AfterEach
    public void deleteRepo() throws DirectoryException, ResourceException {
        Path repoPath = ResourceUtils.getResourceFolderPath("repo");
        DirectoryUtils.deleteAndCreateFolder(repoPath);
    }

    private void checkTraces(){
        Collection<FeatureTrace> currentTraces = this.repository.getFeatureTraces();
        assertTrue(areCollectionsIdenticalByIdentity(currentTraces, this.repoTraces));
        assertTrue(areCollectionsIdenticalByEquality(currentTraces, this.repoTraceCopies));
    }

    public static boolean areCollectionsIdenticalByIdentity(Collection<?> c1, Collection<?> c2) {
        if (c1.size() != c2.size()) {
            return false;
        }
        return c1.stream()
                .allMatch(obj1 -> c2.stream().anyMatch(obj2 -> obj1 == obj2));
    }

    public static boolean areCollectionsIdenticalByEquality(Collection<?> c1, Collection<?> c2) {
        if (c1.size() != c2.size()) {
            return false;
        }
        return c1.stream()
                .allMatch(obj1 -> c2.stream().anyMatch(obj1::equals));
    }

    private void testUnchangedTracesAfterUndoingPreparation(MistakeStrategy mistakeStrategy, int featureTracePercentage, int mistakePercentage){
        MistakeCreator mistakeCreator = new MistakeCreator(mistakeStrategy);
        MemoryListPicker<FeatureTrace> listPicker = new RandomFeatureTracePicker();
        RepositoryPreparator preparator = new RepositoryPreparator(mistakeCreator, listPicker);

        preparator.prepareRepository(this.repository, featureTracePercentage, mistakePercentage, groundTruth);
        preparator.undoPreparation();

        this.checkTraces();
        this.repository.buildMainTree();
        assertTrue(areCollectionsIdenticalByEquality(this.repository.getFeatureTraces(), this.repoTraceCopies));
    }

    @Test
    public void testUnchangedTracesSwappedFeature1(){
        MistakeStrategy mistakeStrategy = new SwappedFeature(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 0, 0);
    }

    @Test
    public void testUnchangedTracesSwappedFeature2(){
        MistakeStrategy mistakeStrategy = new SwappedFeature(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 0, 50);
    }

    @Test
    public void testUnchangedTracesSwappedFeature3(){
        MistakeStrategy mistakeStrategy = new SwappedFeature(runConfig.getFeatures());
    }

    @Test
    public void testUnchangedTracesSwappedFeature4(){
        MistakeStrategy mistakeStrategy = new SwappedFeature(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 50, 0);
    }

    @Test
    public void testUnchangedTracesSwappedFeature5(){
        MistakeStrategy mistakeStrategy = new SwappedFeature(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 50, 50);
    }

    @Test
    public void testUnchangedTracesSwappedFeature6(){
        MistakeStrategy mistakeStrategy = new SwappedFeature(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 50, 100);
    }

    @Test
    public void testUnchangedTracesSwappedFeature7(){
        MistakeStrategy mistakeStrategy = new SwappedFeature(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 100, 0);
    }

    @Test
    public void testUnchangedTracesSwappedFeature8(){
        MistakeStrategy mistakeStrategy = new SwappedFeature(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 100, 50);
    }

    @Test
    public void testUnchangedTracesSwappedFeature9(){
        MistakeStrategy mistakeStrategy = new SwappedFeature(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 100, 100);
    }



    @Test
    public void testUnchangedTracesSwappedCondition1(){
        MistakeStrategy mistakeStrategy = new SwappedCondition();
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 0, 0);
    }

    @Test
    public void testUnchangedTracesSwappedCondition2(){
        MistakeStrategy mistakeStrategy = new SwappedCondition();
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 0, 50);
    }

    @Test
    public void testUnchangedTracesSwappedCondition3(){
        MistakeStrategy mistakeStrategy = new SwappedCondition();
    }

    @Test
    public void testUnchangedTracesSwappedCondition4(){
        MistakeStrategy mistakeStrategy = new SwappedCondition();
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 50, 0);
    }

    @Test
    public void testUnchangedTracesSwappedCondition5(){
        MistakeStrategy mistakeStrategy = new SwappedCondition();
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 50, 50);
    }

    @Test
    public void testUnchangedTracesSwappedCondition6(){
        MistakeStrategy mistakeStrategy = new SwappedCondition();
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 50, 100);
    }

    @Test
    public void testUnchangedTracesSwappedCondition7(){
        MistakeStrategy mistakeStrategy = new SwappedCondition();
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 100, 0);
    }

    @Test
    public void testUnchangedTracesSwappedCondition8(){
        MistakeStrategy mistakeStrategy = new SwappedCondition();
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 100, 50);
    }

    @Test
    public void testUnchangedTracesSwappedCondition9(){
        MistakeStrategy mistakeStrategy = new SwappedCondition();
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 100, 100);
    }



    @Test
    public void testUnchangedTracesErroneousConjunction1(){
        MistakeStrategy mistakeStrategy = new ErroneousConjunction(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 0, 0);
    }

    @Test
    public void testUnchangedTracesErroneousConjunction2(){
        MistakeStrategy mistakeStrategy = new ErroneousConjunction(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 0, 50);
    }

    @Test
    public void testUnchangedTracesErroneousConjunction3(){
        MistakeStrategy mistakeStrategy = new ErroneousConjunction(runConfig.getFeatures());
    }

    @Test
    public void testUnchangedTracesErroneousConjunction4(){
        MistakeStrategy mistakeStrategy = new ErroneousConjunction(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 50, 0);
    }

    @Test
    public void testUnchangedTracesErroneousConjunction5(){
        MistakeStrategy mistakeStrategy = new ErroneousConjunction(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 50, 50);
    }

    @Test
    public void testUnchangedTracesErroneousConjunction6(){
        MistakeStrategy mistakeStrategy = new ErroneousConjunction(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 50, 100);
    }

    @Test
    public void testUnchangedTracesErroneousConjunction7(){
        MistakeStrategy mistakeStrategy = new ErroneousConjunction(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 100, 0);
    }

    @Test
    public void testUnchangedTracesErroneousConjunction8(){
        MistakeStrategy mistakeStrategy = new ErroneousConjunction(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 100, 50);
    }

    @Test
    public void testUnchangedTracesErroneousConjunction9(){
        MistakeStrategy mistakeStrategy = new ErroneousConjunction(runConfig.getFeatures());
        testUnchangedTracesAfterUndoingPreparation(mistakeStrategy, 100, 100);
    }
}
