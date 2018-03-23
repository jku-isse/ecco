package at.jku.isse.ecco.storage.xml;

import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.storage.xml.impl.XmlCommitDao;
import at.jku.isse.ecco.storage.xml.impl.XmlPluginRepositoryDao;
import at.jku.isse.ecco.storage.xml.impl.XmlPluginTransactionStrategy;
import at.jku.isse.ecco.storage.xml.impl.XmlSettingsDao;
import at.jku.isse.ecco.storage.xml.impl.entities.XmlPluginEntityFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;

import java.nio.file.Path;

public class XmlModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(RepositoryDao.class).to(XmlPluginRepositoryDao.class);
        bind(CommitDao.class).to(XmlCommitDao.class);
        bind(SettingsDao.class).to(XmlSettingsDao.class);
        bind(EntityFactory.class).to(XmlPluginEntityFactory.class);

        bind(TransactionStrategy.class).to(XmlPluginTransactionStrategy.class);

        requireBinding(Key.get(Path.class, Names.named("repositoryDir")));
    }
}
