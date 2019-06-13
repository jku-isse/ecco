package at.jku.isse.ecco.storage.ser;

import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerCommitDao;
import at.jku.isse.ecco.storage.ser.dao.SerRemoteDao;
import at.jku.isse.ecco.storage.ser.dao.SerRepositoryDao;
import at.jku.isse.ecco.storage.ser.dao.SerTransactionStrategy;
import com.google.inject.AbstractModule;

public class SerModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(RepositoryDao.class).to(SerRepositoryDao.class);
		bind(CommitDao.class).to(SerCommitDao.class);
		bind(RemoteDao.class).to(SerRemoteDao.class);

		bind(EntityFactory.class).to(MemEntityFactory.class);

		bind(TransactionStrategy.class).to(SerTransactionStrategy.class);
	}

}
