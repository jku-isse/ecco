package at.jku.isse.ecco.storage.mem.repository;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;

/**
 * Memory implementation of {@link Repository}.
 */
public final class MemRepository extends MemAbstractRepository {

	public MemRepository() {
		super(new MemEntityFactory());
	}

}
