package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.rest.classes.RestRepository;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;

import java.util.Map;


@Controller("/api/{rId}/feature")
public class FeatureController {
    private final RepositoryService repositoryService = RepositoryService.getInstance();


    @Post("/{featureId}/description")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestRepository setFeatureDescription(@PathVariable int rId, @PathVariable String featureId, @Body Map<String,String> body) {
        return repositoryService.setFeatureDescription(rId, featureId, body.get("description"));
    }

    @Post("/{featureId}/{revisionId}/description")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestRepository setFeatureRevisionDescription(@PathVariable int rId, @PathVariable String featureId, @PathVariable String revisionId, @Body Map<String,String> body) {
        return repositoryService.setFeatureRevisionDescription(rId, featureId, revisionId, body.get("description"));
    }

    @Post("/pull")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestRepository pullFeaturesRepository(@PathVariable int rId, @Body Map<String,String> body) {
        repositoryService.pullFeaturesRepository(rId, body.get("fromRId"), body.get("selectedFeatures"));
        return repositoryService.getRepository(rId);
    }
}
