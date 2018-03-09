package at.jku.isse.ecco.storage.perst.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.storage.perst.module.PerstModule;
import org.garret.perst.Persistent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class PerstAssociationCounter extends Persistent implements AssociationCounter {

	private Association association;
	private int count;
	private Map<PerstModule, PerstModuleCounter> children;


	public PerstAssociationCounter(Association association) {
		checkNotNull(association);
		this.association = association;
		this.count = 0;
		this.children = new HashMap<>();
	}


	@Override
	public PerstModuleCounter addChild(Module child) {
		if (!(child instanceof PerstModule))
			throw new EccoException("Only PerstModule can be added as a child to PerstAssociationCounter!");
		PerstModule perstChild = (PerstModule) child;
		if (this.children.containsKey(perstChild))
			return null;
		PerstModuleCounter moduleCounter = new PerstModuleCounter(perstChild);
		this.children.put(perstChild, moduleCounter);
		return this.children.get(perstChild);
	}

	@Override
	public PerstModuleCounter getChild(Module child) {
		return this.children.get(child);
	}

	@Override
	public Collection<PerstModuleCounter> getChildren() {
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
