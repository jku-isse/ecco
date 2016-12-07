package at.jku.isse.ecco.plugin;

import at.jku.isse.ecco.dao.*;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;

public class MemModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(RepositoryDao.class).to(MemRepositoryDao.class);
		bind(CommitDao.class).to(MemCommitDao.class);
		bind(SettingsDao.class).to(MemSettingsDao.class);
		bind(EntityFactory.class).to(MemEntityFactory.class);

		requireBinding(Key.get(String.class, Names.named("connectionString")));

		requireBinding(Key.get(String.class, Names.named("clientConnectionString")));
		requireBinding(Key.get(String.class, Names.named("serverConnectionString")));

		bind(TransactionStrategy.class).to(MemTransactionStrategy.class);
	}

}
