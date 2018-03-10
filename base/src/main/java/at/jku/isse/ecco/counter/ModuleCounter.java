package at.jku.isse.ecco.counter;

import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;

import java.util.Collection;

public interface ModuleCounter extends Counter<Module> {

	public default void add(ModuleCounter other) {
		this.incCount(other.getCount());
		// add every module revision in other module to this module
		for (ModuleRevisionCounter otherModuleRevisionCounter : other.getChildren()) {
			ModuleRevisionCounter thisModuleRevisionCounter = this.getChild(otherModuleRevisionCounter.getObject());
			if (thisModuleRevisionCounter == null) {
				thisModuleRevisionCounter = this.addChild(otherModuleRevisionCounter.getObject());
			}
			thisModuleRevisionCounter.add(otherModuleRevisionCounter);
		}
	}


	public ModuleRevisionCounter addChild(ModuleRevision child);

	public ModuleRevisionCounter getChild(ModuleRevision child);

	public Collection<? extends ModuleRevisionCounter> getChildren();


	public default String getModuleCounterString() {
		return this.getObject().getModuleString() + " (" + this.getCount() + "/" + this.getObject().getCount() + ")";
	}

	@Override
	public String toString();

}
