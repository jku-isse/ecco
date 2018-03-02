package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

public class EmptyModuleRevision implements ModuleRevision {

	private FeatureRevision[] pos;
	private Feature[] neg;
	private int count;
	private Module module;


	public EmptyModuleRevision(EmptyModule module) {
		checkNotNull(module);
		this.pos = new FeatureRevision[0];
		this.neg = new Feature[0];
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
		EmptyModuleRevision emptyModuleRevision = (EmptyModuleRevision) o;
		return Arrays.equals(pos, emptyModuleRevision.pos) && Arrays.equals(neg, emptyModuleRevision.neg);
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
