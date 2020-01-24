package at.jku.isse.ecco.storage.jackson;

import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.storage.jackson.dao.*;
import com.google.inject.AbstractModule;

public class JacksonModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(RepositoryDao.class).to(JacksonRepositoryDao.class);
		bind(CommitDao.class).to(JacksonCommitDao.class);
		bind(RemoteDao.class).to(JacksonRemoteDao.class);

		bind(EntityFactory.class).to(JacksonEntityFactory.class);

		bind(TransactionStrategy.class).to(JacksonTransactionStrategy.class);
	}

}
