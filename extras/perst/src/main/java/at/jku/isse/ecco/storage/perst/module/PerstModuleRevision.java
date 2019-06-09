package at.jku.isse.ecco.storage.perst.module;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.ModuleRevision;
import org.garret.perst.Persistent;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PerstModuleRevision extends Persistent implements ModuleRevision {

	private FeatureRevision[] pos;
	private Feature[] neg;
	private int count;
	private PerstModule module;


	public PerstModuleRevision(PerstModule module, FeatureRevision[] pos, Feature[] neg) {
		checkNotNull(module);
		checkNotNull(pos);
		checkNotNull(neg);
		checkArgument(pos.length > 0);
		this.verify(pos, neg);
		this.pos = pos;
		this.neg = neg;
		this.count = 0;
		this.module = module;
	}


	@Override
	public FeatureRevision[] getPos() {
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
	public PerstModule getModule() {
		return this.module;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PerstModuleRevision perstModuleRevision = (PerstModuleRevision) o;

		//return Arrays.equals(pos, perstModuleRevision.pos) && Arrays.equals(neg, perstModuleRevision.neg);
		if (this.pos.length != perstModuleRevision.pos.length || this.neg.length != perstModuleRevision.neg.length)
			return false;
		for (int i = 0; i < this.pos.length; i++) {
			boolean found = false;
			for (int j = 0; j < perstModuleRevision.pos.length; j++) {
				if (this.pos[i].equals(perstModuleRevision.pos[j])) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		for (int i = 0; i < this.neg.length; i++) {
			boolean found = false;
			for (int j = 0; j < perstModuleRevision.neg.length; j++) {
				if (this.neg[i].equals(perstModuleRevision.neg[j])) {
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
		for (FeatureRevision featureRevision : this.pos)
			result += featureRevision.hashCode();
		result *= 31;
		for (Feature feature : this.neg)
			result += feature.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return this.getModuleRevisionString();
	}

}
