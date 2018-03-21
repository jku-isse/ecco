package at.jku.isse.ecco.storage.json.impl;

import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.json.impl.entities.JsonPluginEntityFactory;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import com.google.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;

//There is only one repo
public class JsonPluginRepositoryDao implements RepositoryDao {

    private final JsonPluginTransactionStrategy transactionStrategy;

    @Inject
    public JsonPluginRepositoryDao(JsonPluginTransactionStrategy transactionStrategy, JsonPluginEntityFactory entityFactory) {
        this.transactionStrategy = transactionStrategy;
    }

    @Override
    public Repository.Op load() {
        return transactionStrategy.load();
    }

    @Override
    public void store(Repository.Op repository) {
        if (repository != null && repository instanceof JsonRepository) {
            JsonRepository jsonRepository = (JsonRepository) repository;
            try {
                jsonRepository.storeRepo(transactionStrategy.getRepoPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else
            throw new IllegalStateException("Repository of type '" +
                    (repository == null ? null : repository.getClass()
                            + "' is not supported!"));
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
