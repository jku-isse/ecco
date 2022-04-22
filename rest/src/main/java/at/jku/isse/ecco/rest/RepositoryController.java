package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.rest.classes.RepoHeader;
import at.jku.isse.ecco.rest.classes.RestRepository;
import io.micronaut.http.annotation.*;

@Controller("/api/repository")
public class RepositoryController {
    //TODO change rId into /api/rId/repository
    private RepositoryService repositoryService = RepositoryService.getInstance();

    @Get("/{id}")
    public RestRepository getRepository (@PathVariable int id) {
        return repositoryService.getRepository(id);
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

    @Put("/clone/{OldRId}/{name}")
    public RepoHeader[] cloneRepository(@PathVariable int OldRId, @PathVariable String name) {
        repositoryService.clone(OldRId, name);
        return getAllRepositories();
    }



}
