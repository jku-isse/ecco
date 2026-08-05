package at.jku.isse.ecco.web.domain.repository;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.web.domain.model.ArtifactsPerDepth;
import at.jku.isse.ecco.web.domain.model.AssociationArtifactsModel;
import at.jku.isse.ecco.web.domain.model.AssociationModel;
import at.jku.isse.ecco.web.domain.model.ModulesPerOrder;
import at.jku.isse.ecco.web.rest.EccoApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssociationRepository is a thin wrapper around EccoService.getRepository().getAssociations() -
 * tested against a real repository (core + two branches, so there's more than one association to
 * distinguish) rather than pinning down exact association IDs/counts, which are implementation
 * details of the extraction algorithm, not of this class's own logic. What's verified here is the
 * structural contract: every association shows up exactly once, and the two aggregate views
 * (artifacts-per-depth, modules-per-order) sum to the same totals a direct walk of the real
 * associations produces.
 */
@Timeout(30)
public class AssociationRepositoryTest {

    private Path workDir;
    private EccoApplication application;

    @BeforeEach
    public void setUp() throws IOException {
        workDir = Files.createTempDirectory("association-repository-test");
        application = new EccoApplication();
        application.init(workDir.toString());

        commitVariant("core", "Core");
        commitVariant("branchA", "Core, BranchA", "a.txt");
        commitVariant("branchB", "Core, BranchB", "b.txt");
    }

    @AfterEach
    public void tearDown() {
        application.close();
    }

    private void commitVariant(String name, String configuration, String... extraFileNames) throws IOException {
        Path variantDir = workDir.resolve(name);
        Files.createDirectories(variantDir);
        Files.writeString(variantDir.resolve("core.txt"), "core\n");
        for (String fileName : extraFileNames) {
            Files.writeString(variantDir.resolve(fileName), fileName + "\n");
        }
        EccoService service = application.getEccoService();
        service.setBaseDir(variantDir);
        service.commit(name, configuration);
    }

    private Collection<? extends Association> realAssociations() {
        return application.getEccoService().getRepository().getAssociations();
    }

    @Test
    public void getAssociationsReturnsExactlyOneModelPerRealAssociation() {
        AssociationRepository associationRepository = new AssociationRepository(application);

        AssociationModel[] models = associationRepository.getAssociations();

        List<String> realIds = realAssociations().stream().map(Association::getId).collect(Collectors.toList());
        List<String> modelIds = Arrays.stream(models).map(AssociationModel::getAssociationID).collect(Collectors.toList());
        assertEquals(realIds.size(), modelIds.size());
        assertTrue(modelIds.containsAll(realIds));
        for (AssociationModel model : models) {
            assertNotNull(model.getAssociation());
            assertNotNull(model.getSimpleModuleCondition());
            assertNotNull(model.getSimpleModuleRevisionCondition());
        }
    }

    @Test
    public void getNumberOfArtifactsPerAssociationOmitsEmptyAssociationsAndMatchesRealCounts() {
        AssociationRepository associationRepository = new AssociationRepository(application);

        AssociationArtifactsModel[] models = associationRepository.getNumberOfArtifactsPerAssociation();

        for (AssociationArtifactsModel model : models) {
            assertTrue(model.getNumberOfArtifacts() > 0, "associations with 0 artifacts must be omitted");
        }
        int sumFromModels = Arrays.stream(models).mapToInt(AssociationArtifactsModel::getNumberOfArtifacts).sum();
        int sumFromReal = realAssociations().stream().mapToInt(a -> a.getRootNode().countArtifacts()).sum();
        assertEquals(sumFromReal, sumFromModels);
    }

    @Test
    public void getArtifactsPerDepthTotalsMatchTheSumOfArtifactsAcrossAllAssociations() {
        AssociationRepository associationRepository = new AssociationRepository(application);

        ArtifactsPerDepth[] perDepth = associationRepository.getArtifactsPerDepth();

        assertTrue(perDepth.length > 0);
        int totalFromDepths = Arrays.stream(perDepth).mapToInt(ArtifactsPerDepth::getNumberOfArtifacts).sum();
        int totalFromReal = realAssociations().stream().mapToInt(a -> a.getRootNode().countArtifacts()).sum();
        assertEquals(totalFromReal, totalFromDepths);
    }

    @Test
    public void getModulesPerOrderTotalsMatchTheSumOfModulesAcrossAllAssociations() {
        AssociationRepository associationRepository = new AssociationRepository(application);

        ModulesPerOrder[] perOrder = associationRepository.getModulesPerOrder();

        assertTrue(perOrder.length > 0);
        int totalFromOrders = Arrays.stream(perOrder).mapToInt(ModulesPerOrder::getNumberOfModules).sum();
        int totalFromReal = realAssociations().stream()
                .mapToInt(a -> a.computeCondition().getModules().keySet().size())
                .sum();
        assertEquals(totalFromReal, totalFromOrders);
    }
}
