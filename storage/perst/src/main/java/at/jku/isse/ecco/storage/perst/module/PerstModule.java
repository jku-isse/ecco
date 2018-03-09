package at.jku.isse.ecco.storage.perst.module;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import org.garret.perst.Persistent;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Perst implementation of {@link Feature}.
 */
public class PerstModule extends Persistent implements Module {

	private Feature[] pos;
	private Feature[] neg;
	private int count;
	private Map<PerstModuleRevision, PerstModuleRevision> revisions;


	public PerstModule(Feature[] pos, Feature[] neg) {
		checkNotNull(pos);
		checkNotNull(neg);
		checkArgument(pos.length > 0);
		this.pos = pos;
		this.neg = neg;
		this.count = 0;
		this.revisions = new HashMap<>();
	}


	@Override
	public Feature[] getPos() {
		return this.pos;
	}

	@Override
	public Feature[] getNeg() {
		return this.neg;
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
	public Collection<PerstModuleRevision> getRevisions() {
		return Collections.unmodifiableCollection(this.revisions.values());
	}

	@Override
	public ModuleRevision addRevision(FeatureRevision[] pos, Feature[] neg) {
		if (!this.matchesRevision(pos, neg))
			return null;
		PerstModuleRevision moduleRevision = new PerstModuleRevision(this, pos, neg);
		if (this.revisions.containsKey(moduleRevision))
			return null;
		this.revisions.put(moduleRevision, moduleRevision);
		return moduleRevision;
	}

	@Override
	public ModuleRevision getRevision(FeatureRevision[] pos, Feature[] neg) {
		return this.revisions.get(new PerstModuleRevision(this, pos, neg));
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PerstModule memModule = (PerstModule) o;
		return Arrays.equals(pos, memModule.pos) && Arrays.equals(neg, memModule.neg);
	}

	@Override
	public int hashCode() {
		int result = Arrays.hashCode(pos);
		result = 31 * result + Arrays.hashCode(neg);
		return result;
	}

	@Override
	public String toString() {
		return this.getModuleString();
	}

}
