package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;


public class RepositoryService {
    private final Path repoStorage = Path.of(System.getProperty("user.dir"), "examples");
    private Map<Integer, EccoService> repositories = new TreeMap<>();
    private Map<Integer, String> allRepositories = new TreeMap<>();
    private AtomicInteger rId = new AtomicInteger();
    private EccoService generalService = new EccoService();

    public EccoService getService(int rId) {
        if (repositories.containsKey(rId)) {
            return repositories.get(rId);
        } else {
            EccoService service = new EccoService();
            Path p = repoStorage.resolve(allRepositories.get(rId));
            service.setRepositoryDir(p.resolve(".ecco"));
            service.setBaseDir(p);
            service.open();
            repositories.put(rId, service);
            return service;
        }
    }

    public Repository getRepository(int rId) {
        if (repositories.containsKey(rId)) {
            return repositories.get(rId).getRepository();
        } else {
            EccoService service = new EccoService();
            Path p = repoStorage.resolve(allRepositories.get(rId));
            service.setRepositoryDir(p.resolve(".ecco"));
            service.setBaseDir(p);
            service.open();
            repositories.put(rId, service);
            return service.getRepository();
        }
    }

    public Path getRepoStorage() {
        return repoStorage;
    }

    public Map<Integer, String> getAllRepositories() {
        File folder = new File(repoStorage.toString());
        File[] files = folder.listFiles();

        if (files == null) {
            throw new ResponseStatusException(HttpStatus.NO_CONTENT, "No Repositories found");
        }

        for (final File file : files) {
            if (!allRepositories.containsValue(file.getName())) {
                if (generalService.repositoryExists(file.toPath())) {
                    allRepositories.put(rId.incrementAndGet(), file.getName());
                }
            }
        }
        return allRepositories;
    }
}
