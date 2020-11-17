package at.jku.isse.ecco.storage.mem.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.storage.mem.module.MemModule;
import org.eclipse.collections.impl.factory.Maps;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class MemAssociationCounter implements AssociationCounter {

	public static final long serialVersionUID = 1L;


	private Association association;
	private int count;
	private Map<Module, MemModuleCounter> children;


	public MemAssociationCounter(Association association) {
		checkNotNull(association);
		this.association = association;
		this.count = 0;
		this.children = Maps.mutable.empty();
	}


	@Override
	public ModuleCounter addChild(Module child) {
		if (!(child instanceof MemModule))
			throw new EccoException("Only MemModule can be added as a child to MemAssociationCounter!");
		MemModule memChild = (MemModule) child;
		if (this.children.containsKey(memChild))
			return null;
		MemModuleCounter moduleCounter = new MemModuleCounter(memChild);
		this.children.put(moduleCounter.getObject(), moduleCounter);
		return moduleCounter;
	}

	@Override
	public ModuleCounter getChild(Module child) {
		return this.children.get(child);
	}

	@Override
	public Collection<ModuleCounter> getChildren() {
		return Collections.unmodifiableCollection(this.children.values());
	}

	@Override
	public Association getObject() {
		return this.association;
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
		return this.getAssociationCounterString();
	}

}
