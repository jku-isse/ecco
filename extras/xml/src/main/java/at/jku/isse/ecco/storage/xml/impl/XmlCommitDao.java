package at.jku.isse.ecco.storage.xml.impl;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.CommitDao;
import at.jku.isse.ecco.storage.mem.core.MemCommit;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

public class XmlCommitDao implements CommitDao {

    private final XmlTransactionStrategy transactionStrategy;

    @Inject
    public XmlCommitDao(XmlTransactionStrategy transactionStrategy, final MemEntityFactory entityFactory) {
        this.transactionStrategy = transactionStrategy;
    }

    @Override
    public List<Commit> loadAllCommits() throws EccoException {
        return new ArrayList<>(transactionStrategy.load().getCommitIndex().values());
    }

    @Override
    public Commit load(String id) throws EccoException {
        return transactionStrategy.load().getCommitIndex().get(id);
    }

    @Override
    public void remove(String id) throws EccoException {
        transactionStrategy.load().getCommitIndex().remove(id);
    }

    @Override
    public void remove(Commit entity) throws EccoException {
        transactionStrategy.load().getCommitIndex().remove(entity.getId());
    }

    @Override
    public Commit save(Commit entity) throws EccoException {
        final XmlRepository root = transactionStrategy.load();
        final MemCommit baseEntity = (MemCommit) entity;

        if (!root.getCommitIndex().containsKey(baseEntity.getId())) {
            baseEntity.setId(Integer.toString(root.getCommitIndex().size()));
        }
        while (!root.getCommitIndex().containsKey(baseEntity.getId())) {
            baseEntity.setId(baseEntity.getId() + 1);
        }

        root.getCommitIndex().put(baseEntity.getId(), baseEntity);

        return baseEntity;
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
