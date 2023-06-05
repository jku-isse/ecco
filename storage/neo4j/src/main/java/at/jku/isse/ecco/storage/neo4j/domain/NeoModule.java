package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Transient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@NodeEntity
public class NeoModule extends NeoEntity implements Module {

	@Transient
	private Feature[] pos;

	@Transient
	private Feature[] neg;

	/** arrays do not get hydrated by OGM, using lists instead */

	// no incoming from Feature
	@Relationship(type = "hasPosFeatureMd", direction = Relationship.UNDIRECTED)
	private List<Feature> posList;

	// no incoming from Feature
	@Relationship(type = "hasNegFeatureMd", direction = Relationship.UNDIRECTED)
	private List<Feature> negList;

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
		this.neg = neg;
		this.count = 0;
		this.containingRepository = repository;
		this.posList = new ArrayList<>(Arrays.stream(pos).map(o -> (NeoFeature) o).collect(Collectors.toList()));
		this.negList = new ArrayList<>(Arrays.stream(neg).map(o -> (NeoFeature) o).collect(Collectors.toList()));
	}

	@Override
	public Feature[] getPos() {
    	if (posList != null) {
			pos = posList.stream().toArray(Feature[]::new);
		} else
		{
			this.posList = new ArrayList<>();
			this.pos = new Feature[] {};
		}
    	return this.pos;
	}

	@Override
	public Feature[] getNeg() {
    	if (negList != null) {
			neg = negList.stream().toArray(Feature[]::new);
		} else
		{
			this.negList = new ArrayList<>();
			this.neg = new Feature[] {};
		}
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

		if (this.getPos() != null && neoModule.getPos() != null || this.getNeg() != null && neoModule.getNeg() != null) {
			if (this.getPos().length != neoModule.getPos().length || this.getNeg().length != neoModule.getNeg().length)
				return false;
		}
		if (this.getPos() != null && neoModule.getPos() != null) {
			for (int i = 0; i < this.getPos().length; i++) {
				boolean found = false;
				for (int j = 0; j < neoModule.getPos().length; j++) {
					if (this.getPos()[i].equals(neoModule.getPos()[j])) {
						found = true;
						break;
					}
				}
				if (!found)
					return false;
			}
		}
		if (this.getNeg() != null && neoModule.getNeg() != null) {
			for (int i = 0; i < this.getNeg().length; i++) {
				boolean found = false;
				for (int j = 0; j < neoModule.getNeg().length; j++) {
					if (this.getNeg()[i].equals(neoModule.getNeg()[j])) {
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
		if (this.getPos() != null) {
			for (Feature feature : this.getPos())
				result += feature.hashCode();
			result *= 31;
		}
		if (this.getNeg() != null) {
			for (Feature feature : this.getNeg())
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
