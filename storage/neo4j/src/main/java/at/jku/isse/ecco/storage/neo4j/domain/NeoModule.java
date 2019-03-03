package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@NodeEntity
public class NeoModule extends NeoEntity implements Module {

    @Relationship("hasPosFeature")
	private Feature[] pos;

    @Relationship("hasNegFeature")
	private Feature[] neg;

    @Property("count")
    private int count;

    @Relationship("hasRevision")
	private Collection<NeoModuleRevision> revisions;

    public NeoModule() {}

	public NeoModule(Feature[] pos, Feature[] neg) {
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
	public Collection<NeoModuleRevision> getRevisions() {
		return Collections.unmodifiableCollection(this.revisions);
	}

	@Override
	public NeoModuleRevision addRevision(FeatureRevision[] pos, Feature[] neg) {
		if (!this.matchesRevision(pos, neg))
			return null;
		NeoModuleRevision moduleRevision = new NeoModuleRevision(this, pos, neg);
		if (this.revisions.contains(moduleRevision))
			return null;
		this.revisions.add(moduleRevision);
		return moduleRevision;
	}

	@Override
	public NeoModuleRevision getRevision(FeatureRevision[] pos, Feature[] neg) {
		NeoModuleRevision queryModuleRevision = new NeoModuleRevision(this, pos, neg);
		for (NeoModuleRevision moduleRevision : this.revisions)
			if (moduleRevision.equals(queryModuleRevision))
				return moduleRevision;
		return null;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		NeoModule neoModule = (NeoModule) o;

		//return Arrays.equals(pos, neoModule.pos) && Arrays.equals(neg, neoModule.neg);
		if (this.pos.length != neoModule.pos.length || this.neg.length != neoModule.neg.length)
			return false;
		for (int i = 0; i < this.pos.length; i++) {
			boolean found = false;
			for (int j = 0; j < neoModule.pos.length; j++) {
				if (this.pos[i].equals(neoModule.pos[j])) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		for (int i = 0; i < this.neg.length; i++) {
			boolean found = false;
			for (int j = 0; j < neoModule.neg.length; j++) {
				if (this.neg[i].equals(neoModule.neg[j])) {
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
