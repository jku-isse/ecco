package at.jku.isse.ecco.storage.jackson.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.storage.jackson.module.JacksonModule;
import at.jku.isse.ecco.storage.jackson.module.JacksonModuleRevision;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;

public class JacksonModuleCounter implements ModuleCounter {

	public static final long serialVersionUID = 1L;


	private JacksonModule module;
	private int count;
	private Collection<JacksonModuleRevisionCounter> children;
	//private Map<MemModuleRevision, MemModuleRevisionCounter> children;


	public JacksonModuleCounter(JacksonModule module) {
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
	public JacksonModuleRevisionCounter addChild(ModuleRevision child) {
		if (!(child instanceof JacksonModuleRevision))
			throw new EccoException("Only MemModuleRevision can be added as a child to MemModuleCounter!");
		JacksonModuleRevision memChild = (JacksonModuleRevision) child;
		for (ModuleRevisionCounter moduleRevisionCounter : this.children) {
			if (moduleRevisionCounter.getObject().equals(memChild))
				return null;
		}
		JacksonModuleRevisionCounter moduleRevisionCounter = new JacksonModuleRevisionCounter(memChild);
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
	public Collection<JacksonModuleRevisionCounter> getChildren() {
		return Collections.unmodifiableCollection(this.children);
	}


	@Override
	public JacksonModule getObject() {
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
