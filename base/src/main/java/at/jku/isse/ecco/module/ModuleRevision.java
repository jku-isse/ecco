package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

public interface ModuleRevision {

	/**
	 * The minimum length of this is 1.
	 *
	 * @return The list of positive feature revisions.
	 */
	public FeatureRevision[] getPos();

	public default int getOrder() {
		return this.getPos().length + this.getNeg().length - 1;
	}

	public boolean holds(Configuration configuration);

	public Module getModule();

	public Feature[] getNeg();

	public int getCount();

	public void setCount(int value);

	public void incCount();

}
