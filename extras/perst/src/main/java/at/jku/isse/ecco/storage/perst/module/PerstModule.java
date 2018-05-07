package at.jku.isse.ecco.storage.perst.module;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import org.garret.perst.Persistent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
		this.verify(pos, neg);
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
	public PerstModuleRevision addRevision(FeatureRevision[] pos, Feature[] neg) {
		if (!this.matchesRevision(pos, neg))
			return null;
		PerstModuleRevision moduleRevision = new PerstModuleRevision(this, pos, neg);
		if (this.revisions.containsKey(moduleRevision))
			return null;
		this.revisions.put(moduleRevision, moduleRevision);
		return moduleRevision;
	}

	@Override
	public PerstModuleRevision getRevision(FeatureRevision[] pos, Feature[] neg) {
		return this.revisions.get(new PerstModuleRevision(this, pos, neg));
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PerstModule perstModule = (PerstModule) o;

		//return Arrays.equals(pos, perstModule.pos) && Arrays.equals(neg, perstModule.neg);
		if (this.pos.length != perstModule.pos.length || this.neg.length != perstModule.neg.length)
			return false;
		for (int i = 0; i < this.pos.length; i++) {
			boolean found = false;
			for (int j = 0; j < perstModule.pos.length; j++) {
				if (this.pos[i].equals(perstModule.pos[j])) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		for (int i = 0; i < this.neg.length; i++) {
			boolean found = false;
			for (int j = 0; j < perstModule.neg.length; j++) {
				if (this.neg[i].equals(perstModule.neg[j])) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		int result = 0;
		for (Feature feature : this.pos)
			result += feature.hashCode();
		result *= 31;
		for (Feature feature : this.neg)
			result += feature.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return this.getModuleString();
	}

}
