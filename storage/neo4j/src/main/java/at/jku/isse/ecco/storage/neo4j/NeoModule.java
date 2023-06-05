package at.jku.isse.ecco.storage.neo4j;

import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.storage.neo4j.dao.*;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;

import java.nio.file.Path;

public class NeoModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(RepositoryDao.class).to(NeoRepositoryDao.class);
        bind(CommitDao.class).to(NeoCommitDao.class);
        bind(RemoteDao.class).to(NeoRemoteDao.class);
        bind(EntityFactory.class).to(NeoEntityFactory.class);

        bind(TransactionStrategy.class).to(NeoTransactionStrategy.class);

        requireBinding(Key.get(Path.class, Names.named("repositoryDir")));
    }
}
