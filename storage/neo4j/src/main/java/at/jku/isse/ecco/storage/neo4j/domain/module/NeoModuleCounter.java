package at.jku.isse.ecco.storage.neo4j.domain.module;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.storage.neo4j.domain.NeoEntity;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;

@NodeEntity
public class NeoModuleCounter extends NeoEntity implements ModuleCounter {

    @Relationship("HAS")
	private NeoModule module;

    @Property("count")
	private int count;

    @Relationship("HAS")
	private Collection<NeoModuleRevisionCounter> children;
	//private Map<NeoModuleRevision, NeoModuleRevisionCounter> children;


	public NeoModuleCounter(NeoModule module) {
		checkNotNull(module);
		this.module = module;
		this.count = 0;
		this.children = new ArrayList<>();
		//this.children = Maps.mutable.empty();
		//this.children = HashObjObjMaps.newMutableMap();
	}


//	@Override
//	public NeoModuleRevisionCounter addChild(ModuleRevision child) {
//		if (!(child instanceof NeoModuleRevision))
//			throw new EccoException("Only NeoModuleRevision can be added as a child to NeoModuleCounter!");
//		NeoModuleRevision memChild = (NeoModuleRevision) child;
//		if (this.children.containsKey(memChild))
//			return null;
//		NeoModuleRevisionCounter moduleRevisionCounter = new NeoModuleRevisionCounter(memChild);
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
	public NeoModuleRevisionCounter addChild(ModuleRevision child) {
		if (!(child instanceof NeoModuleRevision))
			throw new EccoException("Only NeoModuleRevision can be added as a child to NeoModuleCounter!");
		NeoModuleRevision memChild = (NeoModuleRevision) child;
		for (ModuleRevisionCounter moduleRevisionCounter : this.children) {
			if (moduleRevisionCounter.getObject().equals(memChild))
				return null;
		}
		NeoModuleRevisionCounter moduleRevisionCounter = new NeoModuleRevisionCounter(memChild);
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
