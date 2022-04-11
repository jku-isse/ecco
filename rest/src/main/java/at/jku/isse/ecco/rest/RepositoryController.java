package at.jku.isse.ecco.rest;


import at.jku.isse.ecco.repository.Repository;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;


@Controller("/api")
public class RepositoryController {
    private RepositoryService repositoryService = new RepositoryService();

    @Get("/test")
    public HttpResponse<?> openRepository() {
            return HttpResponse.status(HttpStatus.OK).body("Test");
    }

    @Get("/repository")
    public Repository getRepository () {
        return repositoryService.getRepository(1);
    }
}
