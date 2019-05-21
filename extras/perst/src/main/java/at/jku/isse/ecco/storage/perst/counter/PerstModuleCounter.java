package at.jku.isse.ecco.storage.perst.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.storage.perst.module.PerstModule;
import at.jku.isse.ecco.storage.perst.module.PerstModuleRevision;
import org.garret.perst.Persistent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;

public class PerstModuleCounter extends Persistent implements ModuleCounter {

	private PerstModule module;
	private int count;
	private Collection<PerstModuleRevisionCounter> children;


	public PerstModuleCounter(PerstModule module) {
		checkNotNull(module);
		this.module = module;
		this.count = 0;
		this.children = new ArrayList<>();
	}


	@Override
	public PerstModuleRevisionCounter addChild(ModuleRevision child) {
		if (!(child instanceof PerstModuleRevision))
			throw new EccoException("Only PerstModuleRevision can be added as a child to PerstModuleCounter!");
		PerstModuleRevision perstChild = (PerstModuleRevision) child;
		for (ModuleRevisionCounter moduleRevisionCounter : this.children) {
			if (moduleRevisionCounter.getObject().equals(perstChild))
				return null;
		}
		PerstModuleRevisionCounter moduleRevisionCounter = new PerstModuleRevisionCounter(perstChild);
		this.children.add(moduleRevisionCounter);
		return moduleRevisionCounter;
	}

	@Override
	public PerstModuleRevisionCounter getChild(ModuleRevision child) {
		for (PerstModuleRevisionCounter moduleRevisionCounter : this.children) {
			if (moduleRevisionCounter.getObject().equals(child))
				return moduleRevisionCounter;
		}
		return null;
	}

	@Override
	public Collection<PerstModuleRevisionCounter> getChildren() {
		return Collections.unmodifiableCollection(this.children);
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


	@Override
	public String toString() {
		return this.getModuleCounterString();
	}

}
