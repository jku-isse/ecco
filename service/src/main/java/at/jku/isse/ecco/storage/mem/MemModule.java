package at.jku.isse.ecco.storage.mem;

import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.storage.mem.dao.*;
import com.google.inject.AbstractModule;

public class MemModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(RepositoryDao.class).to(MemRepositoryDao.class);
		bind(CommitDao.class).to(MemCommitDao.class);
		bind(RemoteDao.class).to(MemRemoteDao.class);

		bind(EntityFactory.class).to(MemEntityFactory.class);

//		requireBinding(Key.get(String.class, Names.named("connectionString")));
//
//		requireBinding(Key.get(String.class, Names.named("clientConnectionString")));
//		requireBinding(Key.get(String.class, Names.named("serverConnectionString")));

		bind(TransactionStrategy.class).to(MemTransactionStrategy.class);
	}

}
