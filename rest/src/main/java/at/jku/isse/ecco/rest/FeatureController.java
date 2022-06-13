package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.rest.classes.RestRepository;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.util.Map;

@Secured(SecurityRule.IS_AUTHENTICATED)
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
        int fromRId = Integer.parseInt(body.get("fromRId"));        //TODO handle exception
        repositoryService.pullFeaturesRepository(rId, fromRId, body.get("deselectedFeatures"));
        return repositoryService.getRepository(rId);
    }
}
