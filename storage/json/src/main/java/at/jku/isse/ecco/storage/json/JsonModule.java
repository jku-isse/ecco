package at.jku.isse.ecco.storage.json;

import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.storage.json.impl.JsonCommitDao;
import at.jku.isse.ecco.storage.json.impl.JsonPluginRepositoryDao;
import at.jku.isse.ecco.storage.json.impl.JsonPluginTransactionStrategy;
import at.jku.isse.ecco.storage.json.impl.JsonSettingsDao;
import at.jku.isse.ecco.storage.json.impl.entities.JsonPluginEntityFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;

import java.nio.file.Path;

public class JsonModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(RepositoryDao.class).to(JsonPluginRepositoryDao.class);
        bind(CommitDao.class).to(JsonCommitDao.class);
        bind(SettingsDao.class).to(JsonSettingsDao.class);
        bind(EntityFactory.class).to(JsonPluginEntityFactory.class);

//		requireBinding(Key.get(String.class, Names.named("connectionString")));
//
//		requireBinding(Key.get(String.class, Names.named("clientConnectionString")));
//		requireBinding(Key.get(String.class, Names.named("serverConnectionString")));

        bind(TransactionStrategy.class).to(JsonPluginTransactionStrategy.class);

        requireBinding(Key.get(Path.class, Names.named("repositoryDir")));
    }
}
