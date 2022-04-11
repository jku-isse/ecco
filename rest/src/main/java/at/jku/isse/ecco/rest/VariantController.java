package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.rest.classes.RestRepository;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;


@Controller("/api/{rId}/variant")
public class VariantController {
    private final RepositoryService repositoryService = RepositoryService.getInstance();

    @Put(uri="/{name}", consumes = MediaType.TEXT_PLAIN)
    public RestRepository addVariant(@PathVariable int rId, @PathVariable String name, @Body String config) {
        return repositoryService.addVariant(rId, name, config);
    }

    @Delete("/{variantId}")
    public RestRepository deleteVariant(@PathVariable int rId, @PathVariable String variantId){
        return repositoryService.removeVariant(rId, variantId);
    }

    @Put("/{variantId}/{featureId}")
    public RestRepository variantAddFeature(@PathVariable int rId, @PathVariable String variantId, @PathVariable String featureId){
        return repositoryService.variantAddFeature(rId, variantId, featureId);
    }

    @Post("/{variantId}/{featureName}")
    public RestRepository variantUpdateFeature(@PathVariable int rId, @PathVariable String variantId, @PathVariable String featureName, @Body String id){
        return repositoryService.variantUpdateFeature(rId, variantId, featureName, id);
    }

    @Delete("/{variantId}/{featureName}")
    public RestRepository variantRemoveFeature(@PathVariable int rId, @PathVariable String variantId, @PathVariable String featureName){
        return repositoryService.variantRemoveFeature(rId, variantId, featureName);
    }
}
