package at.jku.isse.ecco.storage.perst.counter;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.module.Module;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class PerstAssociationCounter implements AssociationCounter {

	private Association association;
	private int count;
	private Map<Module, ModuleCounter> children;


	public PerstAssociationCounter(Association association) {
		checkNotNull(association);
		this.association = association;
		this.count = 0;
		this.children = new HashMap<>();
	}


	@Override
	public ModuleCounter addChild(Module child) {
		if (this.children.containsKey(child))
			return null;
		ModuleCounter moduleCounter = new PerstModuleCounter(child);
		this.children.put(child, moduleCounter);
		return this.children.get(child);
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

}
