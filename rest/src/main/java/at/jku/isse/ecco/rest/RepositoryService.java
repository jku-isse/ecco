package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.rest.classes.RepositoryHandler;
import at.jku.isse.ecco.rest.classes.RestRepository;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.mem.core.MemVariant;
import at.jku.isse.ecco.storage.mem.feature.MemConfiguration;
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
    private static RepositoryService instance;

    private RepositoryService() {

    }
    static {
        instance = new RepositoryService();
    }
    public static RepositoryService getInstance() {
        return instance;
    }


    public RestRepository getRepository(int rId) {
        if (repositories.containsKey(rId)) {
            return repositories.get(rId).getRepository();
        } else {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "repository with the id does not exist");
        }
    }

    public RestRepository createRepository(String name) {
        Path p = getRepoStorage().resolve(name);
        if (p.toFile().exists()) {
            throw new HttpStatusException(HttpStatus.IM_USED, "Repository with this name already exists");
        }
        p.toFile().mkdir();     //create folder

        int newId = rId.incrementAndGet();
        RepositoryHandler newRepo =  new RepositoryHandler(p, newId);
        newRepo.createRepository();
        repositories.put(newId, newRepo);
        return newRepo.getRepository();
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
                    int newId = rId.incrementAndGet();
                    repositories.put(newId, new RepositoryHandler(file.toPath(), newId));
                }
            }
        }
    }

    public RestRepository addVariant(int rId, String name, String config){
        return repositories.get(rId).addVariant(name, config);
    }

    public RestRepository removeVariant(int rId, String variantId){
        return repositories.get(rId).removeVariant(variantId);
    }

    public RestRepository variantAddFeature(int rId, String variantId, String featureId){
        return repositories.get(rId).variantAddFeature(variantId,featureId);
    }

    public RestRepository variantUpdateFeature(int rId, String variantId, String featureName, String id){
        return repositories.get(rId).variantUpdateFeature(variantId, featureName, id);
    }

    public RestRepository variantRemoveFeature(int rId, String variantId, String featureName) {
        return repositories.get(rId).variantRemoveFeature(variantId, featureName);
    }
}
