package at.jku.isse.ecco.storage.mem.counter;

import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.ModuleRevision;

public class MemModuleRevisionCounter implements ModuleRevisionCounter {

	@Override
	public ModuleRevision getObject() {
		return null;
	}

	@Override
	public int getCount() {
		return 0;
	}

	@Override
	public void setCount(int count) {

	}

	@Override
	public void incCount() {

	}

	@Override
	public void incCount(int count) {

	}

}
