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
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static at.jku.isse.ecco.rest.Settings.STORAGE_LOCATION_OF_REPOSITORIES;

public class RepositoryService {
    private final Path repoStorage = Path.of(STORAGE_LOCATION_OF_REPOSITORIES);
    private final Map<Integer, RepositoryHandler> repositories = new TreeMap<>();
    private final AtomicInteger rId = new AtomicInteger();
    private final EccoService generalService = new EccoService();
    private static final RepositoryService instance;
    private static final Logger LOGGER = Logger.getLogger(RepositoryService.class.getName());

    // singleton
    private RepositoryService() {
        getRepositories();
    }

    static {
        instance = new RepositoryService();
        LOGGER.info("Repository service stated");
    }

    public static RepositoryService getInstance() {
        return instance;
    }

    // Repositories ----------------------------------------------------------------------------------------------------
    public RestRepository getRepository(int rId) {
        if (repositories.containsKey(rId)) {
            return repositories.get(rId).getRepository();
        } else {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "repository with the id does not exist");
        }
    }

    public void createRepository(String name) {
        Path p = repoStorage.resolve(name);
        if (p.toFile().exists()) {
            throw new HttpStatusException(HttpStatus.IM_USED, "Repository with this name already exists");
        }
        if(!p.toFile().mkdir()) {      //create folder
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Creation failed: " + p.getFileName().toString());
        }

        int newId = rId.incrementAndGet();      //get new id
        RepositoryHandler newRepo =  new RepositoryHandler(p, newId);
        newRepo.createRepository();
        repositories.put(newId, newRepo);       //add to Map
        LOGGER.info(newId + ": repository created");
    }

    public void cloneRepository(int OldRid, String name) {
        Path oldDir = repositories.get(OldRid).getPath();
        Path newDir = oldDir.getParent().resolve(name);

        if (newDir.toFile().exists()) {
            throw new HttpStatusException(HttpStatus.IM_USED, "Repository with this name already exists");
        }

        try {
            for (Path f: Files.walk(oldDir).toList()) {
                Path destDir = Paths.get(newDir.toString(), f.toString().substring(oldDir.toString().length()));
                Files.copy(f, destDir);
            }
        } catch (IOException e) {
            deleteDirectory(newDir.toFile());
            LOGGER.warning(OldRid + ": could not be cloned");
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "cloning failed");
        }
        LOGGER.info("repository " + OldRid + " cloned");
    }

    public void deleteRepository(final int rId) {
        deleteDirectory(repositories.get(rId).getPath().toFile());
        repositories.remove(rId);
        LOGGER.info(rId + ": repository deleted");
    }

    public Map<Integer, RepositoryHandler> getRepositories() {
        File folder = new File(repoStorage.toString());
        File[] files = folder.listFiles();

        if (files == null) {
            throw new HttpStatusException(HttpStatus.NO_CONTENT, "No Repositories found");
        }

        List<Path> paths =  repositories.values().stream().map(RepositoryHandler::getPath).toList();
        for (final File file : files) {
            if(!paths.contains(file.toPath())) {
                if (generalService.repositoryExists(file.toPath())) {
                    int newId = rId.incrementAndGet();
                    repositories.put(newId, new RepositoryHandler(file.toPath(), newId));
                }
            }
        }
        return repositories;
    }

    // Commit ----------------------------------------------------------------------------------------------------------
    public RestRepository addCommit(int rId, String message, String config, String committer,  CompletedFileUpload[] commitFiles) {

        // Commit storage preparations
        Path commitFolder = repositories.get(rId).getPath().resolve("lastCommit");
        if(commitFolder.toFile().exists()){    //create lastCommit folder if not already existing
            deleteDirectory(commitFolder.toFile());     //remove existing files recursively
        }

        //creates files from uploaded Commit
        for(CompletedFileUpload uploadedFile : commitFiles) {
            File file = commitFolder.resolve(Path.of(uploadedFile.getFilename().substring(1))).toFile();

            File folder = file.getParentFile(); // create folders if they don't exist
            if(!folder.exists()){
                if(!folder.mkdir()) {      //create folder
                    throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Creation failed: " + folder.toString());
                }
            }

            //write file
            try (OutputStream os = new FileOutputStream(file)) {
                os.write(uploadedFile.getBytes());
            } catch (IOException e) {
                LOGGER.warning(rId + ": the committed file" + file.getName() + " could not be created");
                throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "the committed file " + file.getName() + "could not be created");
                //if one of the errors occur the process is terminated and no commit is created
            }
        }

        repositories.get(rId).addCommit(message, config, commitFolder, committer);      //service commit

        LOGGER.info(rId + ": committed");
        return repositories.get(rId).getRepository();
    }

    // Variant ---------------------------------------------------------------------------------------------------------
    public RestRepository addVariant(int rId, String name, String config, String description) {
        return repositories.get(rId).addVariant(name, config, description);
    }

    public RestRepository removeVariant(int rId, String variantId) {
        return repositories.get(rId).removeVariant(variantId);
    }

    public RestRepository variantSetNameDescription(int rId, String variantId, String name, String description){
        return repositories.get(rId).variantSetNameDescription(variantId, name, description);
    }

    public RestRepository variantAddFeature(int rId, String variantId, String featureId) {
        return repositories.get(rId).variantAddFeature(variantId, featureId);
    }

    public RestRepository variantUpdateFeature(int rId, String variantId, String featureName, String id) {
        return repositories.get(rId).variantUpdateFeature(variantId, featureName, id);
    }

    public RestRepository variantRemoveFeature(int rId, String variantId, String featureName) {
        return repositories.get(rId).variantRemoveFeature(variantId, featureName);
    }

    public Path checkout(final int rId, final String variantId) {
        Path checkoutFolder = repositories.get(rId).getPath().resolve("checkout");
        Path checkoutZip = checkoutFolder.getParent().resolve("checkout.zip");

        if(checkoutFolder.toFile().exists()){       //delete old checkout
            if(!deleteDirectory(checkoutFolder.toFile())) {
                throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Deleting old directory is not possible: " + checkoutFolder.getParent().getFileName().toString());
            }
        }
        if(checkoutZip.toFile().exists()){      //delete old checkout zip file
            if(!deleteDirectory(checkoutZip.toFile())) {
                throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Deleting old zip-file is not possible: " + checkoutZip.getParent().getFileName().toString());
            }
        }

        if(!checkoutFolder.toFile().mkdir()) {      //create new checkout folder
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Creation failed: " + checkoutZip.getParent().getFileName().toString());
        }

        repositories.get(rId).checkout(variantId, checkoutFolder);      //service checkout

        try {
            zipFolder(checkoutFolder, checkoutZip);
        } catch (Exception e) {
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create checkout zip file");
        }

        LOGGER.info(rId + ": checked out");
        return checkoutZip;
    }

    // Feature ---------------------------------------------------------------------------------------------------------
    public RestRepository setFeatureDescription(int rId, String featureId, String description) {
        return repositories.get(rId).setFeatureDescription(featureId, description);
    }

    public RestRepository setFeatureRevisionDescription(int rId, String featureId, String revisionId, String description) {
        return repositories.get(rId).setFeatureRevisionDescription(featureId, revisionId, description);
    }

    // private methods -------------------------------------------------------------------------------------------------
    private void zipFolder(Path sourceFolderPath, Path zipPath) throws Exception {
        //from https://www.quickprogrammingtips.com/java/how-to-zip-a-folder-in-java.html
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()));
        Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                zos.putNextEntry(new ZipEntry(sourceFolderPath.relativize(file).toString()));
                Files.copy(file, zos);
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
        zos.close();
    }

    private boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);      //delete recursive
            }
        }
        return directoryToBeDeleted.delete();   //actual deletion
    }
}
