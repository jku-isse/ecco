package at.jku.isse.ecco.storage.ser.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import org.eclipse.collections.impl.factory.Maps;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class SerAssociationCounter implements AssociationCounter {

	public static final long serialVersionUID = 1L;


	private Association association;
	private int count;
	private Map<Module, SerModuleCounter> children;


	public SerAssociationCounter(Association association) {
		checkNotNull(association);
		this.association = association;
		this.count = 0;
		this.children = Maps.mutable.empty();
	}


	@Override
	public ModuleCounter addChild(Module child) {
		if (!(child instanceof SerModule))
			throw new EccoException("Only MemModule can be added as a child to MemAssociationCounter!");
		SerModule memChild = (SerModule) child;
		if (this.children.containsKey(memChild))
			return null;
		SerModuleCounter moduleCounter = new SerModuleCounter(memChild);
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
