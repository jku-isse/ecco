package at.jku.isse.ecco.counter;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Module;

import java.util.Collection;

public interface AssociationCounter extends Counter<Association> {

	public default void add(AssociationCounter other) {
		this.incCount(other.getCount());
		// add every module in other association to this association
		for (ModuleCounter otherModuleCounter : other.getChildren()) {
			ModuleCounter thisModuleCounter = this.getChild(otherModuleCounter.getObject());
			// if the counter for this module does not exist yet add it
			if (thisModuleCounter == null) {
				thisModuleCounter = this.addChild(otherModuleCounter.getObject());
			}
			thisModuleCounter.add(otherModuleCounter);
		}
	}


	public ModuleCounter addChild(Module child);

	public ModuleCounter getChild(Module child);

	public Collection<? extends ModuleCounter> getChildren();


	public default String getAssociationCounterString() {
		return this.getObject().getAssociationString() + " (" + this.getCount() + ")";
	}

	@Override
	public String toString();


	public default void print() {
		System.out.println(this.getAssociationCounterString());
		for (ModuleCounter moduleCounter : this.getChildren()) {
			System.out.println(moduleCounter.getModuleCounterString());
			for (ModuleRevisionCounter moduleRevisionCounter : moduleCounter.getChildren()) {
				System.out.println(moduleRevisionCounter.getModuleRevisionCounterString());
			}
		}
	}

}
