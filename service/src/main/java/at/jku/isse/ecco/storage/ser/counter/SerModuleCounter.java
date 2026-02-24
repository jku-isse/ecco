package at.jku.isse.ecco.storage.ser.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;

public class SerModuleCounter implements ModuleCounter {

	public static final long serialVersionUID = 1L;


	private SerModule module;
	private int count;
	private Collection<SerModuleRevisionCounter> children;
	//private Map<MemModuleRevision, MemModuleRevisionCounter> children;


	public SerModuleCounter(SerModule module) {
		checkNotNull(module);
		this.module = module;
		this.count = 0;
		this.children = new ArrayList<>();
		//this.children = Maps.mutable.empty();
		//this.children = HashObjObjMaps.newMutableMap();
	}


//	@Override
//	public MemModuleRevisionCounter addChild(ModuleRevision child) {
//		if (!(child instanceof MemModuleRevision))
//			throw new EccoException("Only MemModuleRevision can be added as a child to MemModuleCounter!");
//		MemModuleRevision memChild = (MemModuleRevision) child;
//		if (this.children.containsKey(memChild))
//			return null;
//		MemModuleRevisionCounter moduleRevisionCounter = new MemModuleRevisionCounter(memChild);
//		this.children.put(moduleRevisionCounter.getObject(), moduleRevisionCounter);
//		return moduleRevisionCounter;
//	}
//
//	@Override
//	public ModuleRevisionCounter getChild(ModuleRevision child) {
//		return this.children.get(child);
//	}
//
//	@Override
//	public Collection<ModuleRevisionCounter> getChildren() {
//		return Collections.unmodifiableCollection(this.children.values());
//	}

	@Override
	public SerModuleRevisionCounter addChild(ModuleRevision child) {
		if (!(child instanceof SerModuleRevision))
			throw new EccoException("Only MemModuleRevision can be added as a child to MemModuleCounter!");
		SerModuleRevision memChild = (SerModuleRevision) child;
		for (ModuleRevisionCounter moduleRevisionCounter : this.children) {
			if (moduleRevisionCounter.getObject().equals(memChild))
				return null;
		}
		SerModuleRevisionCounter moduleRevisionCounter = new SerModuleRevisionCounter(memChild);
		this.children.add(moduleRevisionCounter);
		return moduleRevisionCounter;
	}

	@Override
	public ModuleRevisionCounter getChild(ModuleRevision child) {
		for (ModuleRevisionCounter moduleRevisionCounter : this.children) {
			if (moduleRevisionCounter.getObject().equals(child))
				return moduleRevisionCounter;
		}
		return null;
	}

	@Override
	public Collection<ModuleRevisionCounter> getChildren() {
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
