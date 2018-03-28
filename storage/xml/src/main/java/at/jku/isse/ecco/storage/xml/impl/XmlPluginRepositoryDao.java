package at.jku.isse.ecco.storage.xml.impl;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import com.google.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static at.jku.isse.ecco.storage.xml.impl.XmlPluginTransactionStrategy.RENAMED_JSON_DB_NAME;
import static java.util.Objects.requireNonNull;

//There is only one repo
public class XmlPluginRepositoryDao implements RepositoryDao {

    private final XmlPluginTransactionStrategy transactionStrategy;

    @Inject
    public XmlPluginRepositoryDao(XmlPluginTransactionStrategy transactionStrategy, MemEntityFactory entityFactory) {
        this.transactionStrategy = transactionStrategy;
    }

    @Override
    public Repository.Op load() {
        return transactionStrategy.load();
    }

    @Override
    public void store(Repository.Op repository) {
        if (repository != null && repository instanceof XmlRepository) {
            XmlRepository jsonRepository = (XmlRepository) repository;
            try {
                storeToFile(jsonRepository);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else
            throw new IllegalStateException("Repository of type '" +
                    (repository == null ? null : repository.getClass()
                            + "' is not supported!"));
    }

    private void storeToFile(XmlRepository repo) throws IOException {
        final XmlRepository repository = requireNonNull(repo);
        final Path tempFileName = transactionStrategy.getTempRepoPath();
        final Path mainFileName = transactionStrategy.getRepoPath();
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
    public void open() {

    }

    @Override
    public void close() {

    }

    @Override
    public void init() {

    }
}
