package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Transient;

import javax.management.relation.Relation;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@NodeEntity
public class NeoModule extends NeoEntity implements Module {

	// no incoming from Feature
    @Relationship(type = "hasPosFeatureMd")
	private Feature[] pos;

	// no incoming from Feature
    @Relationship(type = "hasNegFeatureMd")
	private Feature[] neg;

    @Property("count")
    private int count;

    // incoming from NMR
    @Relationship(value = "hasRevisionMd", direction = Relationship.INCOMING)
	private ArrayList<NeoModuleRevision> revisions  = new ArrayList<>();

	// backref
	@Relationship("hasModulesRp")
	private NeoRepository containingRepository;

    public NeoModule() {}

	public NeoModule(Feature[] pos, Feature[] neg, NeoRepository repository) {
		checkNotNull(pos);
		checkNotNull(neg);
		checkArgument(pos.length > 0);
		this.verify(pos, neg);
		this.pos = pos;
		this.neg =  neg;
		this.count = 0;
		this.containingRepository = repository;
	}

	@Override
	public Feature[] getPos() {
//		if (this.pos == null) {
//			return new Feature[] {};
//		}
    	return this.pos;
	}

	@Override
	public Feature[] getNeg() {
//		if (this.neg == null) {
//			return new Feature[] {};
//		}
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
	public ArrayList<NeoModuleRevision> getRevisions() {
		return this.revisions;
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

		if (this.pos != null && neoModule.pos != null || this.neg != null && neoModule.neg != null) {
			if (this.pos.length != neoModule.pos.length || this.neg.length != neoModule.neg.length)
				return false;
		}
		if (this.pos != null && neoModule.pos != null) {
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
		}
		if (this.neg != null && neoModule.neg != null) {
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
		}
		if (this.getOrder() != neoModule.getOrder()) return false;

		return true;
	}

	@Override
	public int hashCode() {
//		int result = Arrays.hashCode(pos);
//		result = 31 * result + Arrays.hashCode(neg);
//		return result;
		int result = 0;
		if (this.pos != null) {
			for (Feature feature : this.pos)
				result += feature.hashCode();
			result *= 31;
		}
		if (this.neg != null) {
			for (Feature feature : this.neg)
				result += feature.hashCode();
		}
		return result;
	}

	@Override
	public String toString() {
		return this.getModuleString();
	}

	@Override
	public int getOrder() {
    	if (this.getPos() != null && this.getNeg() != null) {
			return this.getPos().length + this.getNeg().length - 1;
		}
    	//TODO: return what instead of 0
    	return 0;
	}
}
