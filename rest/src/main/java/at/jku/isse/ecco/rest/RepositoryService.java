package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.classes.RepositoryHandler;
import at.jku.isse.ecco.rest.classes.RestRepository;
import at.jku.isse.ecco.service.EccoService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;


public class RepositoryService {
    private final Path repoStorage = Path.of(System.getProperty("user.dir"), "examples");
    private Map<Integer, RepositoryHandler> repositories = new TreeMap<>();
    private AtomicInteger rId = new AtomicInteger();
    private EccoService generalService = new EccoService();

    RepositoryService() {
        readRepositories();
    }

    public RestRepository getRepository(int rId) {
        if (repositories.containsKey(rId)) {
            return repositories.get(rId).getRepository();
        } else {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "repository with the id does not exist");
        }
    }

    public Path getRepoStorage() {
        return repoStorage;
    }

    public Map<Integer, RepositoryHandler> getRepositories() {
        readRepositories();
        return repositories;
    }

    public void readRepositories() {
        File folder = new File(repoStorage.toString());
        File[] files = folder.listFiles();

        if (files == null) {
            throw new HttpStatusException(HttpStatus.NO_CONTENT, "No Repositories found");
        }

        for (final File file : files) {
            if(!repositories.values().stream().map(RepositoryHandler::getPath).toList().contains(file.toPath())) {
                if (generalService.repositoryExists(file.toPath())) {
                    repositories.put(rId.incrementAndGet(), new RepositoryHandler(file.toPath()));
                }
            }
        }
    }
}
