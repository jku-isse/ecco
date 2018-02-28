package at.jku.isse.ecco.counter;

import at.jku.isse.ecco.module.ModuleRevision;

public interface ModuleRevisionCounter extends Counter<ModuleRevision> {

	public default void add(ModuleRevisionCounter other) {
		this.incCount(other.getCount());
	}

}
