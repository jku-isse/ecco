package picker;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.experiment.picker.featuretracepicker.SingleAssociationTracePicker;
import at.jku.isse.ecco.experiment.utils.CounterVisitor;
import at.jku.isse.ecco.experiment.utils.DirUtils;
import at.jku.isse.ecco.experiment.utils.ResourceUtils;
import at.jku.isse.ecco.experiment.utils.ServiceUtils;
import at.jku.isse.ecco.experiment.utils.tracecollector.FeatureTraceCollector;
import at.jku.isse.ecco.experiment.utils.vevos.GroundTruth;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class SingleAssociationTracePickerTest {

    private final Path repositoryPath = ResourceUtils.getResourceFolderPath("repo");

    @BeforeEach
    public void deleteExistingRepository(){
        DirUtils.deleteAndCreateDir(this.repositoryPath);
    }

    @Test
    public void pickerPicksNumberEqualToAssociationNumber(){
        Path repositoryPath = ResourceUtils.getResourceFolderPath("repo");
        EccoService eccoService = ServiceUtils.createEccoService(repositoryPath);

        Path variant1Path = ResourceUtils.getResourceFolderPath("Sampling_Base_1/Variant_A");
        Path variant2Path = ResourceUtils.getResourceFolderPath("Sampling_Base_1/Variant_AB");
        Path variant3Path = ResourceUtils.getResourceFolderPath("Sampling_Base_1/Variant_B");
        Path variant4Path = ResourceUtils.getResourceFolderPath("Sampling_Base_1/Variant_Null");
        this.commitVariant(eccoService, variant1Path);
        this.commitVariant(eccoService, variant2Path);
        this.commitVariant(eccoService, variant3Path);
        this.commitVariant(eccoService, variant4Path);

        Repository.Op repository = (Repository.Op) eccoService.getRepository();
        repository.getMainTree();

        Path groundTruthPath = ResourceUtils.getResourceFolderPath("Sampling_Base_1");
        GroundTruth groundTruth = new GroundTruth(groundTruthPath);

        FeatureTraceCollector collector = new FeatureTraceCollector(repository, groundTruth);
        Collection<FeatureTrace> featureTraces = collector.getFeatureTraces();

        SingleAssociationTracePicker picker = new SingleAssociationTracePicker();
        Collection<FeatureTrace> pickedTraces = picker.pickPercentage(featureTraces, 100);

        List<Association> associations = pickedTraces.stream().map(trace -> trace.getNode().getContainingAssociation()).toList();
        Set<Association> associationSet = new HashSet<>(associations);
        assertEquals(associationSet.size(), associations.size());

        List<Association> associationsWithUserTraces = associations.stream().filter(this::associationContainsTracesWithUserCondition).toList();
        assertEquals(associationsWithUserTraces.size(), pickedTraces.size());

        // make sure picked traces have user conditions
        List<FeatureTrace> pickedTracesWithUserCondition = pickedTraces.stream().filter(FeatureTrace::containsUserCondition).toList();
        assertEquals(pickedTraces.size(), pickedTracesWithUserCondition.size());
    }

    private boolean associationContainsTracesWithUserCondition(Association association){
        CounterVisitor counterVisitor = new CounterVisitor();
        Node.Op node = (Node.Op) association.getRootNode();
        node.traverse(counterVisitor);
        return counterVisitor.getUserConditionCount() > 0;
    }

    private void commitVariant(EccoService eccoService, Path variantPath){
        eccoService.setBaseDir(variantPath.toAbsolutePath());
        eccoService.commit();
    }
}
