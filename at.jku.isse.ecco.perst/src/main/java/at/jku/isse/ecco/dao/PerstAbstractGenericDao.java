package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.core.PerstAssociation;
import at.jku.isse.ecco.core.PerstCommit;
import at.jku.isse.ecco.core.PerstVariant;
import at.jku.isse.ecco.feature.PerstFeature;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.garret.perst.FieldIndex;
import org.garret.perst.Storage;
import org.garret.perst.StorageFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Abstract generic perst dao that initializes the database and indexers.
 *
 * @param <T> type of the key
 * @author Hannes Thaller
 * @version 1.0
 */
public abstract class PerstAbstractGenericDao<T extends Persistable> implements GenericDao<T> {

	protected final String connectionString;
	protected Storage database = null;
	protected boolean initialized = false;

	/**
	 * Constructs a new AbstractGenericDao with the given connection string that contains the path to the database file. If no database file exists on the given path than a new
	 * database will be initialized.
	 *
	 * @param connectionString path to the database file
	 */
	@Inject
	PerstAbstractGenericDao(@Named("connectionString") final String connectionString) {
		checkNotNull(connectionString);
		checkArgument(!connectionString.isEmpty());

		this.connectionString = connectionString;
	}

	@Override
	public void init() {
		if (!this.initialized) {
			database = StorageFactory.getInstance().createStorage();

			database.open(connectionString);
			if (database.getRoot() == null) {
				database.setRoot(createDatabaseRoot());
			}
			database.close();

			initialized = true;
		}
	}

	@Override
	public void open() {
		this.openDatabase();
	}

	@Override
	public void close() {
		this.closeDatabase();
	}

	/**
	 * Creates a new root object for a new initialized database.
	 *
	 * @return The root of the database.
	 */
	private DatabaseRoot createDatabaseRoot() {

		final FieldIndex<PerstFeature> featureIndex = database.<PerstFeature>createFieldIndex(PerstFeature.class, "name", true);
		final FieldIndex<PerstAssociation> associationIndex = database.<PerstAssociation>createFieldIndex(PerstAssociation.class, "id", true);
		final FieldIndex<PerstCommit> commitIndex = database.<PerstCommit>createFieldIndex(PerstCommit.class, "id", true);
		final FieldIndex<PerstVariant> variantIndex = database.<PerstVariant>createFieldIndex(PerstVariant.class, "name", true);

		return new DatabaseRoot(associationIndex, featureIndex, commitIndex, variantIndex);
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

}
