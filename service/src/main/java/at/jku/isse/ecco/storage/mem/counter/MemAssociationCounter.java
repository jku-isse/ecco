package at.jku.isse.ecco.storage.mem.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.storage.mem.module.MemModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;

public class MemAssociationCounter implements AssociationCounter {

	public static final long serialVersionUID = 1L;


	private Association association;
	private int count;
	private Collection<MemModuleCounter> children;


	public MemAssociationCounter(Association association) {
		checkNotNull(association);
		this.association = association;
		this.count = 0;
		this.children = new ArrayList<>();
	}


	@Override
	public ModuleCounter addChild(Module child) {
		if (!(child instanceof MemModule))
			throw new EccoException("Only MemModule can be added as a child to MemAssociationCounter!");
		MemModule memChild = (MemModule) child;
		for (ModuleCounter moduleCounter : this.children) {
			if (moduleCounter.getObject().equals(memChild))
				return null;
		}
		MemModuleCounter moduleCounter = new MemModuleCounter(memChild);
		this.children.add(moduleCounter);
		return moduleCounter;
	}

	@Override
	public ModuleCounter getChild(Module child) {
		for (ModuleCounter moduleCounter : this.children) {
			if (moduleCounter.getObject().equals(child))
				return moduleCounter;
		}
		return null;
	}

	@Override
	public Collection<ModuleCounter> getChildren() {
		return Collections.unmodifiableCollection(this.children);
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
