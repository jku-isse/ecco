package at.jku.isse.ecco.counter;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Module;

import java.util.Collection;

public interface AssociationCounter extends Counter<Association> {

	public ModuleCounter addChild(Module child);

	public ModuleCounter getChild(Module child);

	public Collection<ModuleCounter> getChildren();

}
