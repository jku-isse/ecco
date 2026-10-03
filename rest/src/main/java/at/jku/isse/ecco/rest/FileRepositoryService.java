package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.rest.models.RestRepository;
import at.jku.isse.ecco.service.EccoService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import jakarta.inject.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static at.jku.isse.ecco.rest.Settings.STORAGE_LOCATION_OF_REPOSITORIES;

/** "Main" class called by all controllers.
 * Holds all RepositoryHandler
 * only one instance possible (singleton)
 */
@Singleton
public class FileRepositoryService implements RepositoryService {
    private final Path repoStorage;
    // concurrent: requests read it while getRepositories() adds discovered repositories
    private final Map<Integer, RepositoryHandler> repositories = new ConcurrentSkipListMap<>();
    private final AtomicInteger repositoryHandlerId = new AtomicInteger();
    private final EccoService generalService = new EccoService();
    private static final Logger LOGGER = Logger.getLogger(FileRepositoryService.class.getName());

    @Inject
    public FileRepositoryService() {
        this(Path.of(STORAGE_LOCATION_OF_REPOSITORIES));
    }

    /**
     * Not used by Micronaut DI (see the no-arg constructor) - lets tests point repository storage
     * at a temp directory instead of Settings.STORAGE_LOCATION_OF_REPOSITORIES, which resolves to a
     * real, persistent path under this repo's own working tree (examples/).
     */
    public FileRepositoryService(Path repoStorage) {
        this.repoStorage = repoStorage;
    }

    // Repositories ----------------------------------------------------------------------------------------------------
    @Override
    public RestRepository getRepository(int repositoryHandlerId) {
        if (repositories.containsKey(repositoryHandlerId)) {
            return handler(repositoryHandlerId).getRepository();
        } else {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "repository with the id does not exist");
        }
    }

    /**
     * Resolves a client-supplied relative path against {@code base}, rejecting anything that would
     * end up outside it ("../" segments, absolute paths). Upload file names and repository names
     * come straight from the request and used to be resolved unchecked - an arbitrary file write
     * for any authenticated user.
     */
    static Path resolveInside(Path base, String relative) {
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path resolved;
        try {
            Path relativePath = Path.of(relative);
            if (relativePath.isAbsolute()) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid path: " + relative);
            }
            resolved = normalizedBase.resolve(relativePath).normalize();
        } catch (InvalidPathException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid path: " + relative);
        }
        if (!resolved.startsWith(normalizedBase) || resolved.equals(normalizedBase)) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid path: " + relative);
        }
        return resolved;
    }

    /**
     * A repository name must denote exactly one directory directly inside the repository storage.
     */
    private Path resolveRepositoryDir(String name) {
        Path dir = resolveInside(repoStorage, name == null ? "" : name);
        if (!dir.getParent().equals(repoStorage.toAbsolutePath().normalize())) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid repository name: " + name);
        }
        return dir;
    }

    /**
     * The handler for an id, or 404 - lookups used to be plain handler(id).x() calls, so an
     * unknown id was a NullPointerException (500).
     */
    private RepositoryHandler handler(int repositoryHandlerId) {
        RepositoryHandler handler = repositories.get(repositoryHandlerId);
        if (handler == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "repository with the id does not exist");
        }
        return handler;
    }

    @Override
    public synchronized RepositoryHandler createRepository(String name) {
        Path p = resolveRepositoryDir(name);
        if (p.toFile().exists()) {
            throw new HttpStatusException(HttpStatus.IM_USED, "Repository with this name already exists");
        }
        if(!p.toFile().mkdir()) {
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Creation failed: " + p.getFileName().toString());
        }

        int newId = repositoryHandlerId.incrementAndGet();      //get new id
        RepositoryHandler newRepo =  new RepositoryHandler(p, newId);
        newRepo.createRepository();
        repositories.put(newId, newRepo);
        LOGGER.info(newId + ": repository created");
        return repositories.get(newId);
    }

    @Override
    public void forkRepository(int oldRepositoryHandlerId, String name, String disabledFeatures) {
        RepositoryHandler newRepo = createRepository(name);
        newRepo.fork(handler(oldRepositoryHandlerId), disabledFeatures);
    }

    @Override
    public void cloneRepository(int oldRepositoryHandlerId, String name) {
        Path oldDir = handler(oldRepositoryHandlerId).getPath();
        Path newDir = resolveRepositoryDir(name);

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

    @Override
    public void deleteRepository(final int repositoryHandlerId) {
        deleteDirectory(handler(repositoryHandlerId).getPath().toFile());
        repositories.remove(repositoryHandlerId);
        LOGGER.info(repositoryHandlerId + ": repository deleted");
    }

    @Override
    public synchronized Map<Integer, RepositoryHandler> getRepositories() {
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
    @Override
    public RestRepository addCommit(int repositoryHandlerId, String message, String config, String committer, List<CompletedFileUpload> commitFiles) {
        RepositoryHandler repository = handler(repositoryHandlerId);
        // one request at a time per repository: they share its EccoService and the lastCommit folder
        synchronized (repository) {
            return addCommitLocked(repository, repositoryHandlerId, message, config, committer, commitFiles);
        }
    }

    private RestRepository addCommitLocked(RepositoryHandler repository, int repositoryHandlerId, String message, String config, String committer, List<CompletedFileUpload> commitFiles) {
        // a configuration error is the client's, not the server's: refuse it before anything is written
        Configuration configuration;
        try {
            configuration = repository.parseCommitConfiguration(config);
        } catch (EccoException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        Path commitFolder = repository.getPath().resolve("lastCommit");
        if(commitFolder.toFile().exists()){
            deleteDirectory(commitFolder.toFile());     //remove existing files recursively
        }

        // validate every target before writing anything, so a rejected upload leaves no partial commit folder behind
        List<File> targetFiles = new ArrayList<>(commitFiles.size());
        for(CompletedFileUpload uploadedFile : commitFiles) {
            String filename = uploadedFile.getFilename().substring(1);      //substring removes the \ before the filename
            targetFiles.add(resolveInside(commitFolder, filename).toFile());
        }

        // create files from uploaded Commit
        for(int i = 0; i < commitFiles.size(); i++) {
            CompletedFileUpload uploadedFile = commitFiles.get(i);
            File file = targetFiles.get(i);

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

        repository.addCommit(message, configuration, commitFolder, committer);      //handler commit

        LOGGER.info(repositoryHandlerId + ": committed");
        return repository.getRepository();
    }

    // Variant ---------------------------------------------------------------------------------------------------------
    @Override
    public RestRepository addVariant(int repositoryHandlerId, String name, String config, String description) {
        LOGGER.info("Adding Variant");
        return handler(repositoryHandlerId).addVariant(name, config, description);
    }

    @Override
    public RestRepository removeVariant(int repositoryHandlerId, String variantId) {
        return handler(repositoryHandlerId).removeVariant(variantId);
    }

    @Override
    public RestRepository variantSetNameDescription(int repositoryHandlerId, String variantId, String name, String description){
        return handler(repositoryHandlerId).variantSetNameDescription(variantId, name, description);
    }

    @Override
    public RestRepository variantAddFeature(int repositoryHandlerId, String variantId, String featureId) {
        return handler(repositoryHandlerId).variantAddFeature(variantId, featureId);
    }

    @Override
    public RestRepository variantUpdateFeature(int repositoryHandlerId, String variantId, String featureName, String id) {
        return handler(repositoryHandlerId).variantUpdateFeature(variantId, featureName, id);
    }

    @Override
    public RestRepository variantRemoveFeature(int repositoryHandlerId, String variantId, String featureName) {
        return handler(repositoryHandlerId).variantRemoveFeature(variantId, featureName);
    }

    @Override
    public Path checkout(final int repositoryHandlerId, final String variantId) {
        RepositoryHandler repository = handler(repositoryHandlerId);
        // each checkout gets its own folder and zip: the zip is streamed to the client after this
        // method returns, so a fixed checkout.zip could be replaced mid-download by a concurrent
        // checkout of the same repository
        String checkoutName = "checkout-" + UUID.randomUUID();
        Path checkoutFolder = repository.getPath().resolve(checkoutName);
        Path checkoutZip = repository.getPath().resolve(checkoutName + ".zip");
        deleteStaleCheckoutZips(repository.getPath());

        synchronized (repository) {
            if (!checkoutFolder.toFile().mkdir()) {
                throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Creation failed: " + checkoutFolder.getFileName());
            }
            try {
                repository.checkout(variantId, checkoutFolder);      //handler checkout
                zipFolder(checkoutFolder, checkoutZip);
            } catch (HttpStatusException e) {
                throw e;
            } catch (Exception e) {
                throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create checkout zip file");
            } finally {
                deleteDirectory(checkoutFolder.toFile());
            }
        }

        LOGGER.info(repositoryHandlerId + ": checked out");
        return checkoutZip;
    }

    /**
     * Checkout zips are left for the client download that happens after checkout() returns; remove
     * those old enough that any download of them has long finished.
     */
    private static void deleteStaleCheckoutZips(Path repositoryPath) {
        long cutoff = System.currentTimeMillis() - 10 * 60 * 1000;
        File[] zips = repositoryPath.toFile().listFiles((dir, name) -> name.startsWith("checkout") && name.endsWith(".zip"));
        if (zips == null) return;
        for (File zip : zips) {
            if (zip.lastModified() < cutoff) {
                zip.delete();
            }
        }
    }

    // Feature ---------------------------------------------------------------------------------------------------------
    @Override
    public RestRepository setFeatureDescription(int repositoryHandlerId, String featureId, String description) {
        return handler(repositoryHandlerId).setFeatureDescription(featureId, description);
    }

    @Override
    public RestRepository setFeatureRevisionDescription(int repositoryHandlerId, String featureId, String revisionId, String description) {
        return handler(repositoryHandlerId).setFeatureRevisionDescription(featureId, revisionId, description);
    }

    @Override
    public void pullFeaturesRepository(final int toRepositoryHandlerId, final int oldRepositoryHandlerId, final String deselectedFeatures) {
        handler(toRepositoryHandlerId).fork(handler(oldRepositoryHandlerId), deselectedFeatures);     //handler fork
    }

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
