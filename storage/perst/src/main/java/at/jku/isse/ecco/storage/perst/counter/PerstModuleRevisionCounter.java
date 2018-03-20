package at.jku.isse.ecco.storage.perst.counter;

import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.storage.perst.module.PerstModuleRevision;
import org.garret.perst.Persistent;

import static com.google.common.base.Preconditions.checkNotNull;

public class PerstModuleRevisionCounter extends Persistent implements ModuleRevisionCounter {

	private ModuleRevision moduleRevision;
	private int count;


	public PerstModuleRevisionCounter(PerstModuleRevision moduleRevision) {
		checkNotNull(moduleRevision);
		this.moduleRevision = moduleRevision;
		this.count = 0;
	}


	@Override
	public ModuleRevision getObject() {
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
