package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.rest.classes.RestRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;

import java.util.Map;


@Controller("/api/{rId}/variant")
public class VariantController {
    private final RepositoryService repositoryService = RepositoryService.getInstance();

    @Put("/{name}")
    @Consumes(MediaType.TEXT_PLAIN)
    public RestRepository addVariant(@PathVariable int rId, @PathVariable String name, @Body String config) {
        return repositoryService.addVariant(rId, name, config);
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
}
