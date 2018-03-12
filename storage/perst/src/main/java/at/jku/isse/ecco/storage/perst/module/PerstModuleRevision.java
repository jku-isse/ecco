package at.jku.isse.ecco.storage.perst.module;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import org.garret.perst.Persistent;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PerstModuleRevision extends Persistent implements ModuleRevision {

	private FeatureRevision[] pos;
	private Feature[] neg;
	private int count;
	private Module module;


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
	public Module getModule() {
		return this.module;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PerstModuleRevision memModuleRevision = (PerstModuleRevision) o;
		return Arrays.equals(pos, memModuleRevision.pos) && Arrays.equals(neg, memModuleRevision.neg);
	}

	@Override
	public int hashCode() {
		int result = Arrays.hashCode(pos);
		result = 31 * result + Arrays.hashCode(neg);
		return result;
	}

	@Override
	public String toString() {
		return this.getModuleRevisionString();
	}

}
