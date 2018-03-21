package at.jku.isse.ecco.storage.json.impl;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.TransactionStrategy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

@Singleton
public class JsonPluginTransactionStrategy implements TransactionStrategy {

    private static final String JSON_DB_NAME = "ecco.db.json";
    private static final String RENAMED_JSON_DB_NAME = "ecco.db.json.backup";
    private static final String TEMP_JSON_DB_NAME = "ecco.db.json.lock";

    private JsonRepository repo = null;

    public Optional<JsonRepository> getRepository() {
        return Optional.ofNullable(repo);
    }

    public JsonRepository getOrLoadRepository() {
        return internalLoadRepo();
    }


    public JsonRepository load() {
        return getRepository().orElseGet(() -> {
            repo = internalLoadRepo();
            return repo;
        });
    }

    public Path getRepoPath() {
        return repositoryDir.resolve(JSON_DB_NAME);
    }

    private Path getTempRepoPath() {
        return repositoryDir.resolve(TEMP_JSON_DB_NAME);
    }

    private JsonRepository internalLoadRepo() {
        try {
            return JsonRepository.fromRepo(getRepoPath());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private final Path repositoryDir;


    public final TransactionStatus status = new TransactionStatus();


    public Path getRepositoryDir() {
        return repositoryDir;
    }

    @Inject
    public JsonPluginTransactionStrategy(@Named("repositoryDir") Path repositoryDir) {
        this.repositoryDir = requireNonNull(repositoryDir);
    }

    //Only one transaction at a time

    @Override
    public void open() throws EccoException {
    }

    @Override
    public void close() throws EccoException {
        try {
            Files.deleteIfExists(getTempRepoPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void begin() throws EccoException {

    }

    @Override
    public void end() throws EccoException {
        final JsonRepository repository = requireNonNull(repo);
        final Path tempFileName = getTempRepoPath();
        final Path mainFileName = getRepoPath();
        final Path renamedMainFileName = mainFileName.getParent().resolve(RENAMED_JSON_DB_NAME);

        if (!Files.exists(mainFileName)) {
            //Easy: Try to write to main file or fail!
            try {
                repository.storeRepo(mainFileName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            assert Files.exists(mainFileName);
            return;
        }
        //Rename main file, rename temp file, rename the renamed temp file to the main file name
        assert !Files.exists(tempFileName);
        assert !Files.exists(renamedMainFileName);
        assert Files.exists(mainFileName);

        try {
            //Rename main file to backup
            Files.move(mainFileName, renamedMainFileName);
        } catch (IOException e) {
            throw new EccoException("Could not commit repo!", e);
        }

        try {
            //Create temp file
            repository.storeRepo(tempFileName);
            assert Files.exists(tempFileName);
            //Rename temp to main
            Files.move(tempFileName, mainFileName);
        } catch (IOException e) {
            //Try to move the backup back to main file
            try {
                Files.move(renamedMainFileName, mainFileName);
            } catch (IOException e1) {
                //silent
            }
            throw new EccoException("Could not move '" + tempFileName + "' to '" + mainFileName + "... Please do that manually!");
        }

        assert !Files.exists(tempFileName);
        assert Files.exists(renamedMainFileName);
        assert Files.exists(mainFileName);

        try {
            Files.delete(renamedMainFileName);
        } catch (IOException e) {
            throw new EccoException("Could not delete '" + renamedMainFileName + "'. Please delete this file manually and restart the program. No data has been lost!");
        }

        assert !Files.exists(tempFileName);
        assert !Files.exists(renamedMainFileName);
        assert Files.exists(mainFileName);
    }

    @Override
    public void rollback() throws EccoException {
        // Delete temp file and reload repo
        requireNonNull(repo, "No repository found to rollback...");
        try {
            Files.deleteIfExists(getTempRepoPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            JsonRepository rollbackData = requireNonNull(internalLoadRepo());
            repo.rollbackTo(rollbackData);
        }

    }


    private static class TransactionStatus {

        private boolean canStart = true;


        public boolean start() {
            if (!canStart)
                return false;
            canStart = false;
            return true;
        }

        public void release() {
            canStart = true;
        }
    }

    private static class Pair<A, B> {
        private final A first;
        private final B second;

        public A getFirst() {
            return first;
        }

        public B getSecond() {
            return second;
        }

        Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }
}