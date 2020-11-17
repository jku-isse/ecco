package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This is the JPA implementation of the TransactionStrategy.
 * When a transaction is active it returns the corresponding entity manager.
 * When no transaction is active a new entity manager is returned every time.
 */
@Singleton
public class JpaTransactionStrategy implements TransactionStrategy {

	protected final String connectionString;
	//protected EntityManager entityManager;
	protected EntityManagerFactory managerFactory;
	protected boolean initialized = false;


	@Inject
	public JpaTransactionStrategy(@Named("connectionString") final String connectionString) {
		checkNotNull(connectionString);
		checkArgument(!connectionString.isEmpty());

		this.connectionString = connectionString;
	}


	private EntityManager currentEntityManager = null;


	@Override
	public void open() {
		if (!this.initialized) {
			Map<String, String> persistenceMap = new HashMap<>();
			persistenceMap.put("javax.persistence.jdbc.url", "jdbc:derby:" + connectionString + ";create=true");
			persistenceMap.put("javax.persistence.jdbc.user", "username");
			persistenceMap.put("javax.persistence.jdbc.password", "password");
			persistenceMap.put("javax.persistence.jdbc.driver", "org.apache.derby.jdbc.EmbeddedDriver");

//			EntityManagerFactory managerFactory = Persistence.createEntityManagerFactory("ecco", persistenceMap);
//			EntityManager entityManager = managerFactory.createEntityManager();

			this.managerFactory = Persistence.createEntityManagerFactory("ecco", persistenceMap);

			this.initialized = true;
		}
	}

	@Override
	public void close() throws EccoException {

	}


	@Override
	public void begin() throws EccoException {
		this.checkInitialized();

		if (this.currentEntityManager == null) {
			this.currentEntityManager = this.managerFactory.createEntityManager();
			this.currentEntityManager.getTransaction().begin();
		} else
			throw new EccoException("Transaction is already in progress.");
	}

	@Override
	public void end() throws EccoException {
		this.checkInitialized();

		if (this.currentEntityManager == null) {
			throw new EccoException("No transaction in progress.");
		} else {
			this.currentEntityManager.getTransaction().commit();
			this.currentEntityManager = null;
		}
	}

	@Override
	public void rollback() throws EccoException {
		this.checkInitialized();

		if (this.currentEntityManager == null) {
			throw new EccoException("No transaction in progress.");
		} else {
			this.currentEntityManager.getTransaction().rollback();
			this.currentEntityManager = null;
		}
	}


	protected void checkInitialized() throws EccoException {
		if (!this.initialized)
			throw new EccoException("Transaction Strategy has not been initialized.");
	}

	protected EntityManager getEntityManager() throws EccoException {
		this.checkInitialized();

		if (this.currentEntityManager == null)
			return this.managerFactory.createEntityManager();
		else
			return this.currentEntityManager;
	}

}
