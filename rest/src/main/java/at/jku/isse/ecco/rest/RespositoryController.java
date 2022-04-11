package at.jku.isse.ecco.rest;


import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;


@Controller("/api")
public class RespositoryController {
    private RepositoryService repositoryService = new RepositoryService();

    @Get("/test")
    public HttpResponse<?> openRepository() {
            return HttpResponse.status(HttpStatus.OK).body("Test");
    }
}
