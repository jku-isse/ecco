package at.jku.isse.ecco.storage.xml.impl;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.CommitDao;
import at.jku.isse.ecco.storage.xml.impl.entities.XmlCommit;
import at.jku.isse.ecco.storage.xml.impl.entities.XmlPluginEntityFactory;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

public class XmlCommitDao implements CommitDao {

    private final XmlPluginTransactionStrategy transactionStrategy;

    @Inject
    public XmlCommitDao(XmlPluginTransactionStrategy transactionStrategy, final XmlPluginEntityFactory entityFactory) {
        this.transactionStrategy = transactionStrategy;
    }

    @Override
    public List<Commit> loadAllCommits() throws EccoException {
        return new ArrayList<>(transactionStrategy.getOrLoadRepository().getCommitIndex().values());
    }

    @Override
    public Commit load(String id) throws EccoException {
        return transactionStrategy.getOrLoadRepository().getCommitIndex().get(Integer.parseInt(id));
    }

    @Override
    public void remove(String id) throws EccoException {
        transactionStrategy.getOrLoadRepository().getCommitIndex().remove(Integer.parseInt(id));
    }

    @Override
    public void remove(Commit entity) throws EccoException {
        transactionStrategy.getOrLoadRepository().getCommitIndex().remove(entity.getId());
    }

    @Override
    public Commit save(Commit entity) throws EccoException {
        final XmlRepository root = transactionStrategy.getOrLoadRepository();
        final XmlCommit baseEntity = (XmlCommit) entity;

        if (!root.getCommitIndex().containsKey(baseEntity.getId())) {
            baseEntity.setId(root.getCommitIndex().size());
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
