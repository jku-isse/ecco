package at.jku.isse.ecco.plugin;

import at.jku.isse.ecco.dao.*;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;

public class JpaModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AssociationDao.class).to(JpaAssociationDao.class);
		bind(FeatureDao.class).to(JpaFeatureDao.class);
		bind(CommitDao.class).to(JpaCommitDao.class);
		bind(EntityFactory.class).to(JpaEntityFactory.class);

		requireBinding(Key.get(String.class, Names.named("connectionString")));

		requireBinding(Key.get(String.class, Names.named("clientConnectionString")));
		requireBinding(Key.get(String.class, Names.named("serverConnectionString")));

		bind(TransactionStrategy.class).to(JpaTransactionStrategy.class);
	}

//	/**
//	 * Every DAO created from this module must have the same transaction strategy.
//	 */
//	private JpaTransactionStrategy transactionStrategy = null;
//
//	@Provides
//	TransactionStrategy provideTransactionStrategy(@Named("connectionString") String connectionString) {
//		if (this.transactionStrategy == null) {
//			this.transactionStrategy = new JpaTransactionStrategy(connectionString);
//		}
//		return this.transactionStrategy;
//	}

}
