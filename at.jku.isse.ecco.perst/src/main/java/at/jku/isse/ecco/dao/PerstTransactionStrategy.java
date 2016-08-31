package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.PerstAssociation;
import at.jku.isse.ecco.core.PerstCommit;
import at.jku.isse.ecco.core.PerstRemote;
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

	protected int numBegin = 0;

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

			// enable multiclient access
			this.database.setProperty("perst.multiclient.support", Boolean.TRUE);

			this.database.open(connectionString);
			this.database.beginThreadTransaction(Storage.EXCLUSIVE_TRANSACTION);
			if (this.database.getRoot() == null) {
				this.database.setRoot(createDatabaseRoot());
			}
			this.database.endThreadTransaction();
			this.database.close();

			// open database and keep it open
			this.database.open(connectionString);
			this.currentDatabaseRoot = null;

			this.initialized = true;
		}
	}

	@Override
	public void close() {
		if (this.initialized) {
			if (this.database.isOpened())
				this.database.close();

			this.currentDatabaseRoot = null;

			this.initialized = false;
		}
	}

	protected DatabaseRoot createDatabaseRoot() {
		final FieldIndex<PerstFeature> featureIndex = database.<PerstFeature>createFieldIndex(PerstFeature.class, "name", true);
		final FieldIndex<PerstAssociation> associationIndex = database.<PerstAssociation>createFieldIndex(PerstAssociation.class, "id", true);
		final FieldIndex<PerstCommit> commitIndex = database.<PerstCommit>createFieldIndex(PerstCommit.class, "id", true);
		final FieldIndex<PerstVariant> variantIndex = database.<PerstVariant>createFieldIndex(PerstVariant.class, "name", true);
		final FieldIndex<PerstRemote> remoteIndex = database.createFieldIndex(PerstRemote.class, "name", true);

		return new DatabaseRoot(associationIndex, featureIndex, commitIndex, variantIndex, remoteIndex);
	}

	protected void checkInitialized() throws EccoException {
		if (!this.initialized)
			throw new EccoException("Transaction Strategy has not been initialized.");
	}


	@Override
	public void begin() throws EccoException {
		this.checkInitialized();

		this.numBegin++;

		if (this.currentDatabaseRoot == null) {
			this.database.beginThreadTransaction(Storage.EXCLUSIVE_TRANSACTION);

			this.currentDatabaseRoot = this.database.getRoot();
		} else {
			//throw new EccoException("Transaction is already in progress.");
			// do nothing
		}
	}

	public void beginReadOnly() {

	}

	public void beginReadWrite() {

	}

	// TODO: improve transactions!

	@Override
	public void commit() throws EccoException {
		this.checkInitialized();

		if (this.currentDatabaseRoot == null) { // no explicit transaction
			throw new EccoException("No transaction in progress.");
		} else { // explicit transaction
			this.numBegin--;

			if (this.numBegin == 0) {
				this.database.endThreadTransaction();

				this.currentDatabaseRoot = null;
			}
		}
	}

	@Override
	public void rollback() throws EccoException {
		this.checkInitialized();

		if (this.currentDatabaseRoot == null) { // no explicit transaction
			throw new EccoException("No transaction in progress.");
		} else { // explicit transaction
			this.numBegin--;

			if (this.numBegin == 0) {
				this.database.rollbackThreadTransaction();

				this.currentDatabaseRoot = null;
			}
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
