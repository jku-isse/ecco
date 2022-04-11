package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.classes.RestRepository;
import at.jku.isse.ecco.service.EccoService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static at.jku.isse.ecco.rest.Settings.STORAGE_LOCATION_OF_REPOSITORIES;

public class RepositoryService {
    private final Path repoStorage = Path.of(STORAGE_LOCATION_OF_REPOSITORIES);
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

    public RestRepository addCommit(int rId, String message, String config, String committer,  CompletedFileUpload[] commitFiles) {

        // Commit storage preparations
        Path commitFolder = repositories.get(rId).getPath().resolve("lastCommit");
        if(!commitFolder.toFile().exists()){    //create lastCommit folder if not already existing
            commitFolder.toFile().mkdirs();
        }

        deleteDirectory(commitFolder.toFile());     //remove existing files recursively

        //creates files from uploaded Commit
        for(CompletedFileUpload uploadedFile : commitFiles) {
            File file = commitFolder.resolve(Path.of(uploadedFile.getFilename().substring(1))).toFile();

            File folder = file.getParentFile(); // create folders if they don't exist
            if(!folder.exists()){
                folder.mkdirs();
            }

            try (OutputStream os = new FileOutputStream(file)) {
                os.write(uploadedFile.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        repositories.get(rId).addCommit(message, config, commitFolder, committer);

        return repositories.get(rId).getRepository();
    }

    public void clone(int OldRid, String name) {
        getRepositories();
        Path oldDir = repositories.get(OldRid).getPath();
        try {
            Files.walk(oldDir).forEach(source -> copyFile(oldDir.getParent().resolve(name), source));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void copyFile(final Path newPath, final Path source) {
        Path clonedDir = Paths.get(newPath.toString(), source. toString().substring(newPath.toString().length()));
        try {
            Files.copy(source, clonedDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
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
