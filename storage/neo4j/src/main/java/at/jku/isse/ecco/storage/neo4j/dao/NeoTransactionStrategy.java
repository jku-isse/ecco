package at.jku.isse.ecco.storage.neo4j.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.storage.neo4j.NeoSessionFactory;
import at.jku.isse.ecco.storage.neo4j.domain.NeoDatabase;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.transaction.Transaction;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class NeoTransactionStrategy implements TransactionStrategy {

	// repository directory
	private final Path repositoryDir;

	// database file
	private NeoDatabase database;
	// type of current transaction
	private TRANSACTION transaction;
	// number of begin transaction calls
	private int transactionCounter;

    private NeoSessionFactory sessionFactory;


	protected boolean initialized = false;
	private Session currentSession = null;


	@Inject
	public NeoTransactionStrategy(@Named("repositoryDir") final Path repositoryDir) {
		checkNotNull(repositoryDir);
		this.repositoryDir = repositoryDir;
	}

	protected void checkInitialized() throws EccoException {
		if (!this.initialized){
			throw new EccoException("Neo Transaction Strategy has not been initialized");
		}
	}

	protected Session getNeoSession() throws EccoException {
		this.checkInitialized();

		if (this.currentSession == null) {
			return this.sessionFactory.getNeoSession();
		} else {
			return this.currentSession;
		}
	}

	@Override
	public void open() throws EccoException {
		if (!this.initialized) {
			sessionFactory = new NeoSessionFactory(repositoryDir);
			this.initialized = true;
		}

		//session = sessionFactory.getNeoSession();
		//loadDatabase();
//        NeoDatabase test = new NeoDatabase();
//        test.getRepository().addAssociation(new NeoAssociation());
//        session.save(test);
		//https://stackoverflow.com/questions/38884526/neo4j-null-pointer-exception-while-saving-via-repository
	}

	@Override
	public void close() throws EccoException {}

	@Override
	public void begin(TRANSACTION transactionType) throws EccoException {
		this.checkInitialized();

		if (this.currentSession == null) {
			this.currentSession = this.sessionFactory.getNeoSession();
			this.currentSession.beginTransaction(Transaction.Type.READ_WRITE); //TODO: use transactionType?
		} else {
			throw new EccoException("Transaction is already in progress.");
		}
	}

	@Override
	public void end() throws EccoException {
		this.checkInitialized();

		if (this.currentSession == null) {
			throw new EccoException("No transaction in progress.");
		} else {
			this.currentSession.getTransaction().commit();
			this.currentSession.clear();
			this.currentSession = null;
		}


//		try {
//			if (this.transaction == TRANSACTION.READ_ONLY) {
//				session.getTransaction().close();
//			}
//			else if (this.transaction == TRANSACTION.READ_WRITE) {
//				session.save(this.database);
//				session.getTransaction().commit();
//			}
//		} catch (Exception e) {
//			throw new EccoException("Error ending transaction.", e);
//		} finally {
//			if (session.getTransaction() != null) {
//				session.getTransaction().rollback();
//			}
//		}

	}

	@Override
	public void rollback() throws EccoException {
		this.checkInitialized();

		if (this.currentSession == null) {
			throw new EccoException("No transaction in progress.");
		} else {
			this.currentSession.getTransaction().rollback();
			this.currentSession.clear();
			this.currentSession = null;
		}
	}


	public NeoDatabase getDatabase() {
		return this.database;
	}

	public TRANSACTION getTransaction() {
		return this.transaction;
	}


	private void loadDatabase() {

//        Collection<NeoDatabase> db = session.loadAll(NeoDatabase.class);
//
//        if (db.isEmpty()) {
//            this.database = new NeoDatabase();
//        } else {
//            this.database = db.iterator().next(); //TODO: what if more than one result?
//        }
	}



}
