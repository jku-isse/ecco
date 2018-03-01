package at.jku.isse.ecco.storage.mem.counter;

import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;

import java.util.Collection;

public class MemModuleCounter implements ModuleCounter {

	@Override
	public ModuleRevisionCounter addChild(ModuleRevision child) {
		return null;
	}

	@Override
	public ModuleRevisionCounter getChild(ModuleRevision child) {
		return null;
	}

	@Override
	public Collection<ModuleRevisionCounter> getChildren() {
		return null;
	}

	@Override
	public Module getObject() {
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
