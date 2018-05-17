package at.jku.isse.ecco.storage.xml;

import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.storage.xml.impl.XmlCommitDao;
import at.jku.isse.ecco.storage.xml.impl.XmlRepositoryDao;
import at.jku.isse.ecco.storage.xml.impl.XmlTransactionStrategy;
import at.jku.isse.ecco.storage.xml.impl.XmlRemoteDao;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;

import java.nio.file.Path;

public class XmlModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(RepositoryDao.class).to(XmlRepositoryDao.class);
        bind(CommitDao.class).to(XmlCommitDao.class);
        bind(RemoteDao.class).to(XmlRemoteDao.class);
        bind(EntityFactory.class).to(MemEntityFactory.class);

        bind(TransactionStrategy.class).to(XmlTransactionStrategy.class);

        requireBinding(Key.get(Path.class, Names.named("repositoryDir")));
    }
}
