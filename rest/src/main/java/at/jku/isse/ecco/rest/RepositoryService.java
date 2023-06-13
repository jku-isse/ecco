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

/** "Main" class called by all controllers.
 * Holds all RepositoryHandler
 * only one instance possible (singleton)
 */

public class RepositoryService {
    private final Path repoStorage = Path.of(STORAGE_LOCATION_OF_REPOSITORIES);
    private final Map<Integer, RepositoryHandler> repositories = new TreeMap<>();
    private final AtomicInteger repositoryHandlerId = new AtomicInteger();
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
    public RestRepository getRepository(int repositoryHandlerId) {
        if (repositories.containsKey(repositoryHandlerId)) {
            return repositories.get(repositoryHandlerId).getRepository();
        } else {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "repository with the id does not exist");
        }
    }

    public RepositoryHandler createRepository(String name) {
        Path p = repoStorage.resolve(name);
        if (p.toFile().exists()) {
            throw new HttpStatusException(HttpStatus.IM_USED, "Repository with this name already exists");
        }
        if(!p.toFile().mkdir()) {      //create folder
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Creation failed: " + p.getFileName().toString());
        }

        int newId = repositoryHandlerId.incrementAndGet();      //get new id
        RepositoryHandler newRepo =  new RepositoryHandler(p, newId);
        newRepo.createRepository();
        repositories.put(newId, newRepo);       //add to Map
        LOGGER.info(newId + ": repository created");
        return repositories.get(newId);
    }

    public void forkRepository(int oldRepositoryHandlerId, String name, String disabledFeatures) {
        RepositoryHandler newRepo = createRepository(name);
        newRepo.fork(repositories.get(oldRepositoryHandlerId), disabledFeatures);
    }



    //old Methode
    public void cloneRepository(int oldRepositoryHandlerId, String name) {
        Path oldDir = repositories.get(oldRepositoryHandlerId).getPath();
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
            LOGGER.warning(oldRepositoryHandlerId + ": could not be cloned");
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "cloning failed");
        }
        LOGGER.info("repository " + oldRepositoryHandlerId + " cloned");
    }

    public void deleteRepository(final int repositoryHandlerId) {
        deleteDirectory(repositories.get(repositoryHandlerId).getPath().toFile());
        repositories.remove(repositoryHandlerId);
        LOGGER.info(repositoryHandlerId + ": repository deleted");
    }

    public Map<Integer, RepositoryHandler> getRepositories() {
        File folder = new File(repoStorage.toString());
        File[] files = folder.listFiles();

        if (files == null) {
            throw new HttpStatusException(HttpStatus.NO_CONTENT, "No repositories found");
        }

        List<Path> paths =  repositories.values().stream().map(RepositoryHandler::getPath).toList();
        for (final File file : files) {
            if(!paths.contains(file.toPath())) {
                if (generalService.repositoryExists(file.toPath())) {
                    int newId = repositoryHandlerId.incrementAndGet();
                    repositories.put(newId, new RepositoryHandler(file.toPath(), newId));
                }
            }
        }
        return repositories;
    }

    // Commit ----------------------------------------------------------------------------------------------------------
    public RestRepository addCommit(int repositoryHandlerId, String message, String config, String committer,  List<CompletedFileUpload> commitFiles) {

        // Commit storage preparations
        Path commitFolder = repositories.get(repositoryHandlerId).getPath().resolve("lastCommit");
        if(commitFolder.toFile().exists()){
            deleteDirectory(commitFolder.toFile());     //remove existing files recursively
        }

        // create files from uploaded Commit
        for(CompletedFileUpload uploadedFile : commitFiles) {
            File file = commitFolder
                            .resolve(Path.of(uploadedFile.getFilename().substring(1)))
                            .toFile();

            // create folders if they don't exist
            File folder = file.getParentFile();
            if(!folder.exists()){
                if(!folder.mkdirs()) { // create folder + missing parent folders
                    throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Creation failed: " + folder);
                }
            }

            //write file
            try (OutputStream os = new FileOutputStream(file)) {
                os.write(uploadedFile.getBytes());
            } catch (IOException e) {
                LOGGER.warning(repositoryHandlerId + ": the committed file" + file.getName() + " could not be created");
                throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "the committed file " + file.getName() + "could not be created");
                //if one of the errors occur the process is terminated and no commit is created
            }
        }

        repositories.get(repositoryHandlerId).addCommit(message, config, commitFolder, committer);      //handler commit

        LOGGER.info(repositoryHandlerId + ": committed");
        return repositories.get(repositoryHandlerId).getRepository();
    }

    // Variant ---------------------------------------------------------------------------------------------------------
    public RestRepository addVariant(int repositoryHandlerId, String name, String config, String description) {
        LOGGER.info("Adding Variant");
        return repositories.get(repositoryHandlerId).addVariant(name, config, description);
    }

    public RestRepository removeVariant(int repositoryHandlerId, String variantId) {
        return repositories.get(repositoryHandlerId).removeVariant(variantId);
    }

    public RestRepository variantSetNameDescription(int repositoryHandlerId, String variantId, String name, String description){
        return repositories.get(repositoryHandlerId).variantSetNameDescription(variantId, name, description);
    }

    public RestRepository variantAddFeature(int repositoryHandlerId, String variantId, String featureId) {
        return repositories.get(repositoryHandlerId).variantAddFeature(variantId, featureId);
    }

    public RestRepository variantUpdateFeature(int repositoryHandlerId, String variantId, String featureName, String id) {
        return repositories.get(repositoryHandlerId).variantUpdateFeature(variantId, featureName, id);
    }

    public RestRepository variantRemoveFeature(int repositoryHandlerId, String variantId, String featureName) {
        return repositories.get(repositoryHandlerId).variantRemoveFeature(variantId, featureName);
    }

    public Path checkout(final int repositoryHandlerId, final String variantId) {
        Path checkoutFolder = repositories.get(repositoryHandlerId).getPath().resolve("checkout");
        Path checkoutZip = checkoutFolder.getParent().resolve("checkout.zip");

        if(checkoutFolder.toFile().exists()){       //delete old checkout folder
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

        repositories.get(repositoryHandlerId).checkout(variantId, checkoutFolder);      //handler checkout

        try {
            zipFolder(checkoutFolder, checkoutZip);
        } catch (Exception e) {
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create checkout zip file");
        }

        LOGGER.info(repositoryHandlerId + ": checked out");
        return checkoutZip;
    }

    // Feature ---------------------------------------------------------------------------------------------------------
    public RestRepository setFeatureDescription(int repositoryHandlerId, String featureId, String description) {
        return repositories.get(repositoryHandlerId).setFeatureDescription(featureId, description);
    }

    public RestRepository setFeatureRevisionDescription(int repositoryHandlerId, String featureId, String revisionId, String description) {
        return repositories.get(repositoryHandlerId).setFeatureRevisionDescription(featureId, revisionId, description);
    }

    public void pullFeaturesRepository(final int toRepositoryHandlerId, final int oldRepositoryHandlerId, final String deselectedFeatures) {
        repositories.get(toRepositoryHandlerId).fork(repositories.get(oldRepositoryHandlerId), deselectedFeatures);     //handler fork
    }

    // private methods -------------------------------------------------------------------------------------------------
    private void zipFolder(Path sourceFolderPath, Path zipPath) throws Exception {
        //from https://www.quickprogrammingtips.com/java/how-to-zip-a-folder-in-java.html
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()));
        Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<>() {
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