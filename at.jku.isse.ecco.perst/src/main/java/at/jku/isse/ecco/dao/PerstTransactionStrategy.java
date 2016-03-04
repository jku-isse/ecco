package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.PerstAssociation;
import at.jku.isse.ecco.core.PerstCommit;
import at.jku.isse.ecco.core.PerstVariant;
import at.jku.isse.ecco.feature.PerstFeature;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.garret.perst.FieldIndex;
import org.garret.perst.Storage;
import org.garret.perst.StorageFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class PerstTransactionStrategy implements TransactionStrategy {

	protected final String connectionString;
	protected Storage database = null;
	protected boolean initialized = false;


	@Inject
	public PerstTransactionStrategy(@Named("connectionString") final String connectionString) {
		checkNotNull(connectionString);
		checkArgument(!connectionString.isEmpty());

		this.connectionString = connectionString;
	}


	private DatabaseRoot currentDatabaseRoot = null;


	@Override
	public void init() {
		if (!this.initialized) {
			this.database = StorageFactory.getInstance().createStorage();

			this.database.open(connectionString);
			if (this.database.getRoot() == null) {
				this.database.setRoot(createDatabaseRoot());
			}
			this.database.close();

			// open database and keep it open
			this.openDatabase();
			this.currentDatabaseRoot = null;

			this.initialized = true;
		}
	}

	@Override
	public void close() {
		if (this.initialized) {
			this.closeDatabase();
			this.currentDatabaseRoot = null;

			this.initialized = false;
		}
	}

	protected DatabaseRoot createDatabaseRoot() {
		final FieldIndex<PerstFeature> featureIndex = database.<PerstFeature>createFieldIndex(PerstFeature.class, "name", true);
		final FieldIndex<PerstAssociation> associationIndex = database.<PerstAssociation>createFieldIndex(PerstAssociation.class, "id", true);
		final FieldIndex<PerstCommit> commitIndex = database.<PerstCommit>createFieldIndex(PerstCommit.class, "id", true);
		final FieldIndex<PerstVariant> variantIndex = database.<PerstVariant>createFieldIndex(PerstVariant.class, "name", true);

		return new DatabaseRoot(associationIndex, featureIndex, commitIndex, variantIndex);
	}

	protected void checkInitialized() throws EccoException {
		if (!this.initialized)
			throw new EccoException("Transaction Strategy has not been initialized.");
	}

	protected void closeDatabase() {
		if (this.database.isOpened())
			this.database.close();
	}

	protected DatabaseRoot openDatabase() {
		if (!database.isOpened())
			database.open(connectionString);
		return database.getRoot();
	}


	@Override
	public void begin() throws EccoException {
		this.checkInitialized();

		if (this.currentDatabaseRoot == null) {
			this.database.beginThreadTransaction(Storage.EXCLUSIVE_TRANSACTION);

			this.currentDatabaseRoot = this.database.getRoot();
		} else {
			throw new EccoException("Transaction is already in progress.");
		}
	}

	@Override
	public void commit() throws EccoException {
		this.checkInitialized();

		if (this.currentDatabaseRoot == null) { // no explicit transaction
			throw new EccoException("No transaction in progress.");
		} else { // explicit transaction
			this.database.endThreadTransaction();

			this.currentDatabaseRoot = null;
		}
	}

	@Override
	public void rollback() throws EccoException {
		this.checkInitialized();

		if (this.currentDatabaseRoot == null) { // no explicit transaction
			throw new EccoException("No transaction in progress.");
		} else { // explicit transaction
			this.database.rollbackThreadTransaction();

			this.currentDatabaseRoot = null;
		}
	}


	protected DatabaseRoot getDatabaseRoot() throws EccoException {
		this.checkInitialized();

		if (this.currentDatabaseRoot == null) { // no explicit transaction
			return this.database.getRoot();
		} else { // explicit transaction
			return this.currentDatabaseRoot;
		}
	}

	protected void done() {
		if (this.currentDatabaseRoot == null) { // no explicit transaction
			this.database.commit();
		}
	}

}