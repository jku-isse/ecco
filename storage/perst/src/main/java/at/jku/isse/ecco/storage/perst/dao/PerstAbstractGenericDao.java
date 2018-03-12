package at.jku.isse.ecco.storage.perst.dao;

import at.jku.isse.ecco.dao.GenericDao;
import at.jku.isse.ecco.dao.Persistable;
import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Abstract generic perst dao that initializes the database and indexers.
 *
 * @param <T> type of the key
 * @author Hannes Thaller
 * @version 1.0
 */
public abstract class PerstAbstractGenericDao<T extends Persistable> implements GenericDao {

	protected PerstTransactionStrategy transactionStrategy;

//	protected final String connectionString;
//	protected Storage database = null;
//	protected boolean initialized = false;

	/**
	 * Constructs a new AbstractGenericDao with the given connection string that contains the path to the database file. If no database file exists on the given path than a new
	 * database will be initialized.
	 *
	 * @param transactionStrategy the transaction strategy
	 */
	@Inject
	public PerstAbstractGenericDao(PerstTransactionStrategy transactionStrategy) {
		checkNotNull(transactionStrategy);

		this.transactionStrategy = transactionStrategy;
	}

	@Override
	public void init() {
//		if (!this.initialized) {
//			database = StorageFactory.getInstance().createStorage();
//
//			database.open(connectionString);
//			if (database.getRoot() == null) {
//				database.setRoot(createDatabaseRoot());
//			}
//			database.close();
//
//			initialized = true;
//		}
	}

	@Override
	public void open() {
		//this.openDatabase();
	}

	@Override
	public void close() {
		//this.closeDatabase();
	}

//	/**
//	 * Creates a new root object for a new initialized database.
//	 *
//	 * @return The root of the database.
//	 */
//	private DatabaseRoot createDatabaseRoot() {
//
//		final FieldIndex<PerstFeature> featureIndex = database.<PerstFeature>createFieldIndex(PerstFeature.class, "name", true);
//		final FieldIndex<PerstAssociation> associationIndex = database.<PerstAssociation>createFieldIndex(PerstAssociation.class, "id", true);
//		final FieldIndex<PerstCommit> commitIndex = database.<PerstCommit>createFieldIndex(PerstCommit.class, "id", true);
//		final FieldIndex<PerstVariant> variantIndex = database.<PerstVariant>createFieldIndex(PerstVariant.class, "name", true);
//
//		return new DatabaseRoot(associationIndex, featureIndex, commitIndex, variantIndex);
//	}
//
//	protected void closeDatabase() {
//		if (this.database.isOpened())
//			this.database.close();
//	}
//
//	protected DatabaseRoot openDatabase() {
//		if (!database.isOpened())
//			database.open(connectionString);
//		return database.getRoot();
//	}

}
