package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.rest.classes.RestRepository;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.util.Map;

@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/api/{rId}/variant")
public class VariantController {
    private final RepositoryService repositoryService = RepositoryService.getInstance();

    @Put("/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestRepository addVariant(@PathVariable int rId, @PathVariable String name, @Body Map<String,String> body) {
        return repositoryService.addVariant(rId, name, body.get("configuration"), body.get("description"));
    }

    @Delete("/{variantId}")
    public RestRepository deleteVariant(@PathVariable int rId, @PathVariable String variantId){
        return repositoryService.removeVariant(rId, variantId);
    }

    @Post("/{variantId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestRepository variantSetNameDescription(@PathVariable int rId, @PathVariable String variantId, @Body Map<String,String> body){
        return repositoryService.variantSetNameDescription(rId, variantId, body.get("name"), body.get("description"));
    }

    @Put("/{variantId}/{featureId}")
    public RestRepository variantAddFeature(@PathVariable int rId, @PathVariable String variantId, @PathVariable String featureId){
        return repositoryService.variantAddFeature(rId, variantId, featureId);
    }

    @Post("/{variantId}/{featureName}")
    @Consumes(MediaType.TEXT_PLAIN)
    public RestRepository variantUpdateFeature(@PathVariable int rId, @PathVariable String variantId, @PathVariable String featureName, @Body String id){
        return repositoryService.variantUpdateFeature(rId, variantId, featureName, id);
    }

    @Delete("/{variantId}/{featureName}")
    public RestRepository variantRemoveFeature(@PathVariable int rId, @PathVariable String variantId, @PathVariable String featureName){
        return repositoryService.variantRemoveFeature(rId, variantId, featureName);
    }

    @Produces(value = "application/checkout.zip")
    @Get("/{variantId}/checkout")
    public SystemFile checkoutVariant(@PathVariable int rId, @PathVariable String variantId) {
        return new SystemFile(repositoryService.checkout(rId, variantId).toFile());
    }
}
