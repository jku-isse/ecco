package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.rest.classes.RepoHeader;
import at.jku.isse.ecco.rest.classes.RestRepository;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;


@Controller("/api")
public class RepositoryController {
    private final RepositoryService repositoryService = new RepositoryService();

    @Get("/repository/{id}")
    public RestRepository getRepository (@PathVariable int id) {
        return repositoryService.getRepository(id);
    }

    @Get("/repository/all")
    public RepoHeader[] getAllRepositories() {
        return repositoryService.getRepositories().entrySet().stream().map(e -> new RepoHeader(e.getKey(), e.getValue().getName())).toArray(RepoHeader[]::new);
    }

    @Put("/{name}")           //create Repository
    public RepoHeader[] create(@PathVariable String name) {
        repositoryService.createRepository(name);
        return getAllRepositories();
    }

}
