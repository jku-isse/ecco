package at.jku.isse.ecco.storage.mem.counter;

import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.storage.mem.module.MemModuleRevision;

import static com.google.common.base.Preconditions.checkNotNull;

public class MemModuleRevisionCounter implements ModuleRevisionCounter {

	public static final long serialVersionUID = 1L;


	private MemModuleRevision moduleRevision;
	private int count;


	public MemModuleRevisionCounter(MemModuleRevision moduleRevision) {
		checkNotNull(moduleRevision);
		this.moduleRevision = moduleRevision;
		this.count = 0;
	}


	@Override
	public MemModuleRevision getObject() {
		return this.moduleRevision;
	}

	@Override
	public int getCount() {
		return this.count;
	}

	@Override
	public void setCount(int count) {
		this.count = count;
	}

	@Override
	public void incCount() {
		this.count++;
	}

	@Override
	public void incCount(int count) {
		this.count += count;
	}


	@Override
	public String toString() {
		return this.getModuleRevisionCounterString();
	}

}
