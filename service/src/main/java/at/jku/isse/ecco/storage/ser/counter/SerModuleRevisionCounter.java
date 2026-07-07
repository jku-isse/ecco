package at.jku.isse.ecco.storage.ser.counter;

import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;

import static com.google.common.base.Preconditions.checkNotNull;

public class SerModuleRevisionCounter implements ModuleRevisionCounter {

	public static final long serialVersionUID = 1L;


	private SerModuleRevision moduleRevision;
	private int count;


	public SerModuleRevisionCounter(SerModuleRevision moduleRevision) {
		checkNotNull(moduleRevision);
		this.moduleRevision = moduleRevision;
		this.count = 0;
	}


	@Override
	public SerModuleRevision getObject() {
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
