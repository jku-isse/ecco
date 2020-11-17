package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class EmptyModule implements Module {

	private Feature[] pos;
	private Feature[] neg;
	private int count;
	private EmptyModuleRevision emptyModuleRevision;


	public EmptyModule() {
		this.pos = new Feature[0];
		this.neg = new Feature[0];
		this.count = 0;
		this.emptyModuleRevision = new EmptyModuleRevision(this);
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
	public Collection<ModuleRevision> getRevisions() {
		return Collections.unmodifiableCollection(Arrays.asList(this.emptyModuleRevision));
	}

	@Override
	public ModuleRevision addRevision(FeatureRevision[] pos, Feature[] neg) {
		return null;
	}

	@Override
	public ModuleRevision getRevision(FeatureRevision[] pos, Feature[] neg) {
		if (pos.length == 0 && neg.length == 0)
			return this.emptyModuleRevision;
		else
			return null;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		EmptyModule emptyModule = (EmptyModule) o;
		return Arrays.equals(pos, emptyModule.pos) && Arrays.equals(neg, emptyModule.neg);
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
