package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.rest.models.RestRepository;
import jakarta.inject.*;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.util.Map;

@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/api/{repositoryHandlerId}/variant")
public class VariantController {
    private final RepositoryService repositoryService;

    @Inject
    public VariantController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Put("/{variantName}")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestRepository addVariant(@PathVariable int repositoryHandlerId, @PathVariable String variantName, @Body Map<String, String> body) {
        return repositoryService.addVariant(repositoryHandlerId, variantName, body.get("configuration"), body.get("description"));
    }

    @Delete("/{variantId}")
    public RestRepository deleteVariant(@PathVariable int repositoryHandlerId, @PathVariable String variantId) {
        return repositoryService.removeVariant(repositoryHandlerId, variantId);
    }

    @Post("/{variantId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestRepository variantSetNameDescription(@PathVariable int repositoryHandlerId, @PathVariable String variantId, @Body Map<String, String> body) {
        return repositoryService.variantSetNameDescription(repositoryHandlerId, variantId, body.get("name"), body.get("description"));
    }

    @Put("/{variantId}/{featureId}")
    public RestRepository variantAddFeature(@PathVariable int repositoryHandlerId, @PathVariable String variantId, @PathVariable String featureId) {
        return repositoryService.variantAddFeature(repositoryHandlerId, variantId, featureId);
    }

    @Post("/{variantId}/{featureName}")
    @Consumes(MediaType.TEXT_PLAIN)
    public RestRepository variantUpdateFeature(@PathVariable int repositoryHandlerId, @PathVariable String variantId, @PathVariable String featureName, @Body String id) {
        return repositoryService.variantUpdateFeature(repositoryHandlerId, variantId, featureName, id);
    }

    @Delete("/{variantId}/{featureName}")
    public RestRepository variantRemoveFeature(@PathVariable int repositoryHandlerId, @PathVariable String variantId, @PathVariable String featureName) {
        return repositoryService.variantRemoveFeature(repositoryHandlerId, variantId, featureName);
    }

    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Get("/{variantId}/checkout")
    public SystemFile checkoutVariant(@PathVariable int repositoryHandlerId, @PathVariable String variantId) {
        return new SystemFile(repositoryService.checkout(repositoryHandlerId, variantId).toFile());
    }
}
