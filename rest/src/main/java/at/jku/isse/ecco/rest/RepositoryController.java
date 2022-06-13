package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.rest.classes.RepoHeader;
import at.jku.isse.ecco.rest.classes.RestRepository;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.util.Map;

@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/api/repository")
public class RepositoryController {
    private final RepositoryService repositoryService = RepositoryService.getInstance();

    @Get("/{rId}")
    public RestRepository getRepository (@PathVariable int rId) {
        return repositoryService.getRepository(rId);
    }

    @Get("/all")
    public RepoHeader[] getAllRepositories() {
        return repositoryService.getRepositories().entrySet().stream().map(e -> new RepoHeader(e.getKey(), e.getValue().getName())).toArray(RepoHeader[]::new);
    }

    @Put("/{name}")           //create Repository
    public RepoHeader[] create(@PathVariable String name) {
        repositoryService.createRepository(name);
        return getAllRepositories();
    }

    //old Methode
    @Put("/clone/{oldRId}/{name}")
    public RepoHeader[] cloneRepository(@PathVariable int oldRId, @PathVariable String name) {
        repositoryService.cloneRepository(oldRId, name);
        return getAllRepositories();
    }

    @Put("/fork/{oldRId}/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    public RepoHeader[] forkRepository(@PathVariable int oldRId, @PathVariable String name, @Body Map<String,String> body) {
        repositoryService.forkRepository(oldRId, name, body.get("deselectedFeatures"));
        return getAllRepositories();
    }

    @Delete("/{rId}")
    public RepoHeader[] deleteRepository(@PathVariable int rId) {
        repositoryService.deleteRepository(rId);
        return getAllRepositories();
    }
}
