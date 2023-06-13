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

    @Get("/{repositoryHandlerId}")      //return Repository
    public RestRepository getRepository (@PathVariable int repositoryHandlerId) {
        return repositoryService.getRepository(repositoryHandlerId);
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

    @Put("/clone/{oldRepositoryHandlerId}/{repositoryName}")
    public RepoHeader[] cloneRepository(@PathVariable int oldRepositoryHandlerId, @PathVariable String repositoryName) {
        repositoryService.cloneRepository(oldRepositoryHandlerId, repositoryName);
        return getAllRepositories();
    }

    @Put("/fork/{oldRepositoryHandlerId}/{newRepositoryName}")
    @Consumes(MediaType.APPLICATION_JSON)
    public RepoHeader[] forkRepository(@PathVariable int oldRepositoryHandlerId, @PathVariable String newRepositoryName, @Body Map<String,String> body) {
        repositoryService.forkRepository(oldRepositoryHandlerId, newRepositoryName, body.get("deselectedFeatures"));
        return getAllRepositories();
    }

    @Delete("/{repositoryHandlerId}")       //delete Repository
    public RepoHeader[] deleteRepository(@PathVariable int repositoryHandlerId) {
        repositoryService.deleteRepository(repositoryHandlerId);
        return getAllRepositories();
    }
}
