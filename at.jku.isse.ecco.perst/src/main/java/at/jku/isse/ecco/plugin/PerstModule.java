package at.jku.isse.ecco.plugin;

import at.jku.isse.ecco.dao.*;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;

/**
 * Bootstraps the database module to the interfaces provided by the core module.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AssociationDao.class).to(PerstAssociationDao.class);
		bind(FeatureDao.class).to(PerstFeatureDao.class);
		bind(CommitDao.class).to(PerstCommitDao.class);
		bind(EntityFactory.class).to(PerstEntityFactory.class);

		requireBinding(Key.get(String.class, Names.named("connectionString")));

		requireBinding(Key.get(String.class, Names.named("clientConnectionString")));
		requireBinding(Key.get(String.class, Names.named("serverConnectionString")));

		bind(TransactionStrategy.class).to(PerstTransactionStrategy.class);
	}

}
