package at.jku.isse.ecco.storage.neo4j.impl;

import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import com.google.inject.Inject;

//There is only one repo
public class NeoRepositoryDao implements RepositoryDao {

    private final NeoTransactionStrategy transactionStrategy;

    @Inject
    public NeoRepositoryDao(NeoTransactionStrategy transactionStrategy, MemEntityFactory entityFactory) {
        this.transactionStrategy = transactionStrategy;
    }


    @Override
    public Repository.Op load() {
        return transactionStrategy.load();
    }

    @Override
    public void store(Repository.Op repository) {

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
