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

    @Get("/{rId}")      //return Repository
    public RestRepository getRepository (@PathVariable int rId) {
        return repositoryService.getRepository(rId);
    }

    @Get("/all")
    public RepoHeader[] getAllRepositories() {
        return repositoryService
                .getRepositories()
                .entrySet()
                .stream()
                .map(e -> new RepoHeader(e.getKey(), e.getValue().getName()))
                .toArray(RepoHeader[]::new);
    }

    @Put("/{repositoryName}")           //create Repository
    public RepoHeader[] create(@PathVariable String repositoryName) {
        repositoryService.createRepository(repositoryName);
        return getAllRepositories();
    }

    @Put("/clone/{oldRId}/{repositoryName}")      //old Methode
    public RepoHeader[] cloneRepository(@PathVariable int oldRId, @PathVariable String repositoryName) {
        repositoryService.cloneRepository(oldRId, repositoryName);
        return getAllRepositories();
    }

    @Put("/fork/{oldRId}/{newRepositoryName}")       //forks given repository (with selected features) to new Repository with given @newRepositoryName
    @Consumes(MediaType.APPLICATION_JSON)
    public RepoHeader[] forkRepository(@PathVariable int oldRId, @PathVariable String newRepositoryName, @Body Map<String,String> body) {
        repositoryService.forkRepository(oldRId, newRepositoryName, body.get("deselectedFeatures"));
        return getAllRepositories();
    }

    @Delete("/{rId}")       //delete Repository
    public RepoHeader[] deleteRepository(@PathVariable int rId) {
        repositoryService.deleteRepository(rId);
        return getAllRepositories();
    }
}
