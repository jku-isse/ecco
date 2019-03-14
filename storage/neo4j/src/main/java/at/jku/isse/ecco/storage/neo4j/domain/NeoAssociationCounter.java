package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.module.Module;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

@NodeEntity
public class NeoAssociationCounter extends NeoEntity implements AssociationCounter {

    @Relationship("hasAssociationAc")
	private Association association;

    @Property("countAc")
	private int count;

    @Relationship(type = "hasChildAC", direction = "INCOMING")
	private ArrayList<NeoModuleCounter> children  = new ArrayList<>();

    public NeoAssociationCounter() {}

	public NeoAssociationCounter(Association association) {
		checkNotNull(association);
		this.association = association;
		this.count = 0;
	}


	@Override
	public ModuleCounter addChild(Module child) {
		if (!(child instanceof NeoModule))
			throw new EccoException("Only PerstModule can be added as a child to PerstAssociationCounter!");
		NeoModule memChild = (NeoModule) child;
		for (ModuleCounter moduleCounter : this.children) {
			if (moduleCounter.getObject().equals(memChild))
				return null;
		}
		NeoModuleCounter moduleCounter = new NeoModuleCounter(memChild);
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
