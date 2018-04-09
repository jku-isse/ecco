package at.jku.isse.ecco.storage.mem.module;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link Module}.
 */
public class MemModule implements Module {

	public static final long serialVersionUID = 1L;


	private Feature[] pos;
	private Feature[] neg;
	private int count;
	private Collection<MemModuleRevision> revisions;


	public MemModule(Feature[] pos, Feature[] neg) {
		checkNotNull(pos);
		checkNotNull(neg);
		checkArgument(pos.length > 0);
		this.verify(pos, neg);
		this.pos = pos;
		this.neg = neg;
		this.count = 0;
		this.revisions = new ArrayList<>();
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
	public Collection<MemModuleRevision> getRevisions() {
		return Collections.unmodifiableCollection(this.revisions);
	}

	@Override
	public MemModuleRevision addRevision(FeatureRevision[] pos, Feature[] neg) {
		if (!this.matchesRevision(pos, neg))
			return null;
		MemModuleRevision moduleRevision = new MemModuleRevision(this, pos, neg);
		if (this.revisions.contains(moduleRevision))
			return null;
		this.revisions.add(moduleRevision);
		return moduleRevision;
	}

	@Override
	public MemModuleRevision getRevision(FeatureRevision[] pos, Feature[] neg) {
		MemModuleRevision queryModuleRevision = new MemModuleRevision(this, pos, neg);
		for (MemModuleRevision moduleRevision : this.revisions)
			if (moduleRevision.equals(queryModuleRevision))
				return moduleRevision;
		return null;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		MemModule memModule = (MemModule) o;

		//return Arrays.equals(pos, memModule.pos) && Arrays.equals(neg, memModule.neg);
		if (this.pos.length != memModule.pos.length || this.neg.length != memModule.neg.length)
			return false;
		for (int i = 0; i < this.pos.length; i++) {
			boolean found = false;
			for (int j = 0; j < memModule.pos.length; j++) {
				if (this.pos[i].equals(memModule.pos[j])) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		for (int i = 0; i < this.neg.length; i++) {
			boolean found = false;
			for (int j = 0; j < memModule.neg.length; j++) {
				if (this.neg[i].equals(memModule.neg[j])) {
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
//		int result = Arrays.hashCode(pos);
//		result = 31 * result + Arrays.hashCode(neg);
//		return result;
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
