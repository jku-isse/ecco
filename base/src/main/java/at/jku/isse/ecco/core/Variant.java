package at.jku.isse.ecco.core;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Configuration;

/**
 * A Variant has a name and a configuration. It is used as an easier way to refer to configurations, mostly during a checkout.
 */
public interface Variant extends Persistable {

	public String getName();

	public void setName(String name);

	public String getDescription();

	public void setDescription(String description);

	public Configuration getConfiguration();

	public void setConfiguration(Configuration configuration);

}
