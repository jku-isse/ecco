package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.rest.models.RestRepository;
import com.google.inject.*;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.util.Map;

@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/api/{repositoryHandlerId}/feature")
public class FeatureController {
    private final RepositoryService repositoryService;

    @Inject
    public FeatureController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Post("/{featureId}/description")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestRepository setFeatureDescription(@PathVariable int repositoryHandlerId, @PathVariable String featureId, @Body Map<String,String> body) {
        return repositoryService.setFeatureDescription(repositoryHandlerId, featureId, body.get("description"));
    }

    @Post("/{featureId}/{revisionId}/description")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestRepository setFeatureRevisionDescription(@PathVariable int repositoryHandlerId, @PathVariable String featureId, @PathVariable String revisionId, @Body Map<String,String> body) {
        return repositoryService.setFeatureRevisionDescription(repositoryHandlerId, featureId, revisionId, body.get("description"));
    }

    @Post("/pull")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestRepository pullFeaturesRepository(@PathVariable int repositoryHandlerId, @Body Map<String,String> body) {
        int fromRepositoryHandlerId = Integer.parseInt(body.get("fromRepositoryHandlerId"));
        repositoryService.pullFeaturesRepository(repositoryHandlerId, fromRepositoryHandlerId, body.get("deselectedFeatures"));
        return repositoryService.getRepository(repositoryHandlerId);
    }
}
