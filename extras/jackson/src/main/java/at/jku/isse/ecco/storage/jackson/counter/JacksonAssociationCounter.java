package at.jku.isse.ecco.storage.jackson.counter;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.storage.jackson.module.JacksonModule;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.eclipse.collections.impl.factory.Maps;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class JacksonAssociationCounter implements AssociationCounter {

	public static final long serialVersionUID = 1L;


	@JsonBackReference
	private Association association;
	private int count;
	private Map<String, JacksonModuleCounter> children;


	public JacksonAssociationCounter(Association association) {
		checkNotNull(association);
		this.association = association;
		this.count = 0;
		this.children = Maps.mutable.empty();
	}


	@Override
	public ModuleCounter addChild(Module child) {
		if (!(child instanceof JacksonModule))
			throw new EccoException("Only MemModule can be added as a child to MemAssociationCounter!");
		JacksonModule memChild = (JacksonModule) child;
		if (this.children.containsKey(memChild.toString()))
			return null;
		JacksonModuleCounter moduleCounter = new JacksonModuleCounter(memChild);
		this.children.put(moduleCounter.getObject().toString(), moduleCounter);
		return moduleCounter;
	}

	@Override
	public ModuleCounter getChild(Module child) {
		return this.children.get(child.toString());
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
