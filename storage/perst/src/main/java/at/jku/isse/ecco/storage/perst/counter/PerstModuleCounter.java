package at.jku.isse.ecco.storage.perst.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.storage.perst.module.PerstModule;
import at.jku.isse.ecco.storage.perst.module.PerstModuleRevision;
import org.garret.perst.Persistent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class PerstModuleCounter extends Persistent implements ModuleCounter {

	private PerstModule module;
	private int count;
	private Map<PerstModuleRevision, PerstModuleRevisionCounter> children;


	public PerstModuleCounter(PerstModule module) {
		checkNotNull(module);
		this.module = module;
		this.count = 0;
		this.children = new HashMap<>();
	}


	@Override
	public PerstModuleRevisionCounter addChild(ModuleRevision child) {
		if (!(child instanceof PerstModuleRevision))
			throw new EccoException("Only PerstModuleRevision can be added as a child to PerstModuleCounter!");
		PerstModuleRevision perstChild = (PerstModuleRevision) child;
		if (this.children.containsKey(perstChild))
			return null;
		PerstModuleRevisionCounter moduleRevisionCounter = new PerstModuleRevisionCounter(perstChild);
		this.children.put(perstChild, moduleRevisionCounter);
		return this.children.get(perstChild);
	}

	@Override
	public ModuleRevisionCounter getChild(ModuleRevision child) {
		return this.children.get(child);
	}

	@Override
	public Collection<PerstModuleRevisionCounter> getChildren() {
		return Collections.unmodifiableCollection(this.children.values());
	}

	@Override
	public Module getObject() {
		return this.module;
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

}
