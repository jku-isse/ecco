package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.rest.classes.RestRepository;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;


@Controller("/api/{rId}/feature")
public class FeatureController {
    private final RepositoryService repositoryService = RepositoryService.getInstance();


    @Post("/{featureId}/description")
    @Consumes(MediaType.TEXT_PLAIN)
    public RestRepository setFeatureDescription(@PathVariable int rId, @PathVariable String featureId, @Body String description) {
        return repositoryService.setFeatureDescription(rId, featureId, description);
    }

    @Post("/{featureId}/{revisionId}/description")
    @Consumes(MediaType.TEXT_PLAIN)
    public RestRepository setFeatureRevisionDescription(@PathVariable int rId, @PathVariable String featureId, @PathVariable String revisionId, @Body String description) {
        return repositoryService.setFeatureRevisionDescription(rId, featureId, revisionId, description);
    }
}
