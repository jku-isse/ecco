package at.jku.isse.ecco.storage.mem.counter;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class MemAssociationCounter implements AssociationCounter {

	private Module module;
	private int count;
	private Map<Module, Collection<ModuleRevision>> children;


	public MemAssociationCounter(Module module) {
		checkNotNull(module);
		this.module = module;
		this.count = 0;
		this.children = new HashMap<>();
	}


	@Override
	public ModuleCounter addChild(Module child) {
		return null;
	}

	@Override
	public ModuleCounter getChild(Module child) {
		return null;
	}

	@Override
	public Collection<ModuleCounter> getChildren() {
		return null;
	}

	@Override
	public Association getObject() {
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
