package at.jku.isse.ecco.storage.mem.counter;

import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class MemModuleCounter implements ModuleCounter {

	private Module module;
	private int count;
	private Map<ModuleRevision, ModuleRevisionCounter> children;


	public MemModuleCounter(Module module) {
		checkNotNull(module);
		this.module = module;
		this.count = 0;
		this.children = new HashMap<>();
	}


	@Override
	public ModuleRevisionCounter addChild(ModuleRevision child) {
		if (this.children.containsKey(child))
			return null;
		ModuleRevisionCounter moduleRevisionCounter = new MemModuleRevisionCounter(child);
		this.children.put(child, moduleRevisionCounter);
		return this.children.get(child);
	}

	@Override
	public ModuleRevisionCounter getChild(ModuleRevision child) {
		return this.children.get(child);
	}

	@Override
	public Collection<ModuleRevisionCounter> getChildren() {
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


	@Override
	public String toString() {
		return this.getModuleCounterString();
	}

}
