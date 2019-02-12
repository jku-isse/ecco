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
	// write file channel
	private FileChannel writeFileChannel;
	// write file lock
	private FileLock writeFileLock;

    private NeoSessionFactory sessionFactory;
    private Session session; //TODO: good practice?


	@Inject
	public NeoTransactionStrategy(@Named("repositoryDir") final Path repositoryDir) {
		checkNotNull(repositoryDir);
		this.repositoryDir = repositoryDir;

		this.reset();
	}


	public NeoDatabase getDatabase() {
		return this.database;
	}

	public TRANSACTION getTransaction() {
		return this.transaction;
	}


	@Override
	public synchronized void open() {
		this.reset();

        sessionFactory = new NeoSessionFactory(repositoryDir);
        session = sessionFactory.getNeoSession();
        loadDatabase();
//        NeoDatabase test = new NeoDatabase();
//        test.getRepository().addAssociation(new NeoAssociation());
//        session.save(test);
        //https://stackoverflow.com/questions/38884526/neo4j-null-pointer-exception-while-saving-via-repository

        this.transactionCounter = 0;
	}

	@Override
	public synchronized void close() {
		if (this.transaction != null || this.transactionCounter != 0)
			throw new EccoException("Error closing connection: Not all transactions have been ended.");
		this.reset();
	}

	@Override
	public synchronized void rollback() {
		if (this.transaction == null && this.transactionCounter == 0 || session.getTransaction() == null) {
            throw new EccoException("Error rolling back transaction: No transaction active.");
        } else
        {
            session.getTransaction().rollback();
        }
		this.reset();
	}


	@Override
	public synchronized void begin(TRANSACTION transaction) {

		try {
			if (transaction == TRANSACTION.READ_ONLY) {
                session.beginTransaction(Transaction.Type.READ_ONLY);
                this.transaction = TRANSACTION.READ_ONLY;
            }
			else if (transaction == TRANSACTION.READ_WRITE) {
                session.beginTransaction(Transaction.Type.READ_WRITE);
                this.transaction = TRANSACTION.READ_WRITE;
            }
			this.transactionCounter++;

			loadDatabase();
		} catch (Exception e) {
			throw new EccoException("Error beginning transaction.", e);
		}

	}


	/**
	 * Ends a transaction.
	 */
	@Override
	public synchronized void end() {
		if (this.transaction == null || this.transactionCounter <= 0)
			throw new EccoException("There is no active transaction.");

		this.transactionCounter--;
		if (this.transactionCounter == 0) {
			try {
				if (this.transaction == TRANSACTION.READ_ONLY) {
                    session.getTransaction().close();
                }
				else if (this.transaction == TRANSACTION.READ_WRITE) {
				    session.save(this.database);
				    session.getTransaction().commit();
                }
			} catch (Exception e) {
				throw new EccoException("Error ending transaction.", e);
			} finally {
			    if (session.getTransaction() != null) {
                    session.getTransaction().rollback();
                }
            }
            this.transaction = null;
		}
	}

	private void reset() {
		this.database = null;
		this.transaction = null;
		this.transactionCounter = 0;
		this.writeFileChannel = null;
		this.writeFileLock = null;

		/*if (session!= null) {

            session.clear();
        }*/
	}

	private void loadDatabase() {

        Collection<NeoDatabase> db = session.loadAll(NeoDatabase.class);

        if (db.isEmpty()) {
            this.database = new NeoDatabase();
        } else {
            this.database = db.iterator().next(); //TODO: what if more than one result?
        }
	}



}
