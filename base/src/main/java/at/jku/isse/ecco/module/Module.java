package at.jku.isse.ecco.module;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;

import java.util.Collection;
import java.util.Set;

/**
 * Contains a set of features that make up the module.
 * <p>
 * A module is a set of features and has an order. A module with an order of zero is a core module, a module with an order greater than 0 is a derivative module.
 * <p>
 * NOTE: this class does not need to be persistable.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public interface Module extends Persistable, Set<ModuleFeature>, Iterable<ModuleFeature>, Collection<ModuleFeature> {

	/**
	 * Returns the order of the modules which is the amount of features that are derived.
	 *
	 * @return The order of the module.
	 */
	default int getOrder() {
		return this.size() - 1;
	}

	public boolean holds(Configuration configuration);

}
