package at.jku.isse.ecco.counter;

import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;

import java.util.Collection;

public interface ModuleCounter extends Counter<Module> {

	public ModuleRevisionCounter addChild(ModuleRevision child);

	public ModuleRevisionCounter getChild(ModuleRevision child);

	public Collection<ModuleRevisionCounter> getChildren();

}
