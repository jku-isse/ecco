package at.jku.isse.ecco.storage.ser;

import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerCommitDao;
import at.jku.isse.ecco.storage.ser.dao.SerRepositoryDao;
import at.jku.isse.ecco.storage.ser.dao.SerSettingsDao;
import at.jku.isse.ecco.storage.ser.dao.SerTransactionStrategy;
import com.google.inject.AbstractModule;

public class SerModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(RepositoryDao.class).to(SerRepositoryDao.class);
		bind(CommitDao.class).to(SerCommitDao.class);
		bind(SettingsDao.class).to(SerSettingsDao.class);

		bind(EntityFactory.class).to(MemEntityFactory.class);

		bind(TransactionStrategy.class).to(SerTransactionStrategy.class);
	}

}
