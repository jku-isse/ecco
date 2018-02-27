package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 *
 */
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

	public void setCount(int count);

	public void incCount();

	public void incCount(int count);


	@Override
	public int hashCode();

	@Override
	public boolean equals(Object object);


	public default String getModuleRevisionString() {
		String moduleRevisionString = Arrays.stream(this.getPos()).map(featureRevision -> featureRevision.toString()).collect(Collectors.joining(", "));
		if (this.getNeg().length > 0)
			moduleRevisionString += Arrays.stream(this.getNeg()).map(feature -> feature.toString()).collect(Collectors.joining(", "));

		return "d^" + this.getOrder() + "(" + moduleRevisionString + ")";
	}

	@Override
	public String toString();

}
