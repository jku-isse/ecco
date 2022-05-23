package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.rest.classes.RestRepository;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;

import java.util.Map;


@Controller("/api/{rId}/feature")
public class FeatureController {
    private final RepositoryService repositoryService = RepositoryService.getInstance();


    @Post("/{featureId}/description")
    @Consumes(MediaType.TEXT_PLAIN)
    public RestRepository setFeatureDescription(@PathVariable int rId, @PathVariable String featureId, @Body String description) {
        return repositoryService.setFeatureDescription(rId, featureId, description == null ? "" : description);     //TODO check
    }

    @Post("/{featureId}/{revisionId}/description")
    @Consumes(MediaType.TEXT_PLAIN)
    public RestRepository setFeatureRevisionDescription(@PathVariable int rId, @PathVariable String featureId, @PathVariable String revisionId, @Body String description) {
        return repositoryService.setFeatureRevisionDescription(rId, featureId, revisionId, description == null ? "" : description);     //TODO check
    }

    @Post("/pull")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestRepository pullFeaturesRepository(@PathVariable int rId, @Body Map<String,String> body) {
        repositoryService.pullFeaturesRepository(rId, body.get("fromRId"), body.get("selectedFeatures"));
        return repositoryService.getRepository(rId);
    }
}
