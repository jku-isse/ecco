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

	/** See {@link SerModuleCounter#resolveModule} - same reasoning, one level down. */
	public synchronized void resolveModuleRevision(SerModuleRevision moduleRevision) {
		this.moduleRevision = moduleRevision;
	}


	@Override
	public synchronized SerModuleRevision getObject() {
		return this.moduleRevision;
	}

	// Synchronized for the same reason as SerModuleCounter's count/children (see its class javadoc,
	// association-counter-unsynchronized-race in project memory): getCount() is read concurrently by
	// Association.Op#computeLikelyCondition()/#computeCertainCondition() while incCount() is written
	// during commit's addObservation() - the identical race, one level down the same counter tree.
	@Override
	public synchronized int getCount() {
		return this.count;
	}

	@Override
	public synchronized void setCount(int count) {
		this.count = count;
	}

	@Override
	public synchronized void incCount() {
		this.count++;
	}

	@Override
	public synchronized void incCount(int count) {
		this.count += count;
	}


	@Override
	public String toString() {
		return this.getModuleRevisionCounterString();
	}

}
