package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.models.RestRepository;
import io.micronaut.http.server.types.files.SystemFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * VariantController is a plain Micronaut-annotated POJO - its methods can be called directly
 * against a mocked RepositoryService without standing up the embedded HTTP server (see RestTest
 * for the full-stack alternative), so these just check the routing/argument-forwarding logic.
 */
public class VariantControllerTest {

    @Test
    public void addVariantForwardsConfigurationAndDescriptionFromTheRequestBody() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        RestRepository expected = mock(RestRepository.class);
        when(repositoryService.addVariant(1, "release-1", "Core", "first release")).thenReturn(expected);
        VariantController controller = new VariantController(repositoryService);

        RestRepository result = controller.addVariant(1, "release-1", Map.of("configuration", "Core", "description", "first release"));

        assertSame(expected, result);
    }

    @Test
    public void deleteVariantDelegatesToRepositoryService() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        RestRepository expected = mock(RestRepository.class);
        when(repositoryService.removeVariant(1, "variant-id")).thenReturn(expected);
        VariantController controller = new VariantController(repositoryService);

        RestRepository result = controller.deleteVariant(1, "variant-id");

        assertSame(expected, result);
    }

    @Test
    public void variantSetNameDescriptionForwardsNameAndDescriptionFromTheRequestBody() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        RestRepository expected = mock(RestRepository.class);
        when(repositoryService.variantSetNameDescription(1, "variant-id", "renamed", "new description")).thenReturn(expected);
        VariantController controller = new VariantController(repositoryService);

        RestRepository result = controller.variantSetNameDescription(1, "variant-id", Map.of("name", "renamed", "description", "new description"));

        assertSame(expected, result);
    }

    @Test
    public void variantAddFeatureDelegatesToRepositoryService() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        RestRepository expected = mock(RestRepository.class);
        when(repositoryService.variantAddFeature(1, "variant-id", "feature-id")).thenReturn(expected);
        VariantController controller = new VariantController(repositoryService);

        RestRepository result = controller.variantAddFeature(1, "variant-id", "feature-id");

        assertSame(expected, result);
    }

    @Test
    public void variantUpdateFeatureForwardsTheRawRequestBodyAsTheRevisionId() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        RestRepository expected = mock(RestRepository.class);
        when(repositoryService.variantUpdateFeature(1, "variant-id", "Core", "rev-2")).thenReturn(expected);
        VariantController controller = new VariantController(repositoryService);

        RestRepository result = controller.variantUpdateFeature(1, "variant-id", "Core", "rev-2");

        assertSame(expected, result);
    }

    @Test
    public void variantRemoveFeatureDelegatesToRepositoryService() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        RestRepository expected = mock(RestRepository.class);
        when(repositoryService.variantRemoveFeature(1, "variant-id", "Core")).thenReturn(expected);
        VariantController controller = new VariantController(repositoryService);

        RestRepository result = controller.variantRemoveFeature(1, "variant-id", "Core");

        assertSame(expected, result);
    }

    @Test
    public void checkoutVariantWrapsTheReturnedPathInASystemFile() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        Path zipPath = Path.of("/tmp/checkout.zip");
        when(repositoryService.checkout(1, "variant-id")).thenReturn(zipPath);
        VariantController controller = new VariantController(repositoryService);

        SystemFile result = controller.checkoutVariant(1, "variant-id");

        assertEquals(zipPath.toFile(), result.getFile());
    }
}
