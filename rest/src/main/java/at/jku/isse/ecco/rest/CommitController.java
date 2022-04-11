package at.jku.isse.ecco.rest;

import io.micronaut.http.annotation.Controller;

@Controller("/api/commit")
public class CommitController {
    private RepositoryService repositoryService = RepositoryService.getInstance();


}
