package at.jku.isse.ecco.dao;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public abstract class JpaAbstractGenericDao<T extends Persistable> implements GenericDao<T> {

	protected final String connectionString;
	protected boolean initialized = false;
	protected EntityManager entityManager;

	@Inject
	JpaAbstractGenericDao(@Named("connectionString") final String connectionString) {
		checkNotNull(connectionString);
		checkArgument(!connectionString.isEmpty());

		this.connectionString = connectionString;
	}

	@Override
	public void init() {
		if (!this.initialized) {
			Map<String, String> persistenceMap = new HashMap<>();
			persistenceMap.put("javax.persistence.jdbc.url", "<url>");
			persistenceMap.put("javax.persistence.jdbc.user", "<username>");
			persistenceMap.put("javax.persistence.jdbc.password", "<password>");
			persistenceMap.put("javax.persistence.jdbc.driver", "<driver>");

			EntityManagerFactory managerFactory = Persistence.createEntityManagerFactory("ecco", persistenceMap);
			entityManager = managerFactory.createEntityManager();

			initialized = true;
		}
	}

	@Override
	public void open() {

	}

	@Override
	public void close() {

	}

}
