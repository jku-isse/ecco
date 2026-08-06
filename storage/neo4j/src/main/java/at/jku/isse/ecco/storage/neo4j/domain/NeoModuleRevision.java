package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
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
public class NeoModuleRevision extends NeoEntity implements ModuleRevision {

	@Transient
	private FeatureRevision[] pos;

	@Transient
	private Feature[] neg;

	/** arrays do not get hydrated by OGM, using lists instead */

	// no incoming from Feature
	@Relationship(type = "hasPosFeatureRevisionRv", direction = Relationship.UNDIRECTED)
	private List<FeatureRevision> posList;

	// no incoming from Feature
	@Relationship(type = "hasNegFeatureRevisionRv", direction = Relationship.UNDIRECTED)
	private List<Feature> negList;

    @Property("count")
	private int count;

    // backref
    @Relationship("hasModuleRv")
	private NeoModule module;

    public NeoModuleRevision() {}

	public NeoModuleRevision(NeoModule module, FeatureRevision[] pos, Feature[] neg) {
		checkNotNull(module);
		checkNotNull(pos);
		checkNotNull(neg);
		checkArgument(pos.length > 0);
		this.verify(pos, neg);
		this.pos = pos;
		this.neg = neg;
		this.count = 0;
		this.module = module;

		this.posList = new ArrayList<>(Arrays.stream(pos).map(o -> (NeoFeatureRevision) o).collect(Collectors.toList()));
		this.negList = new ArrayList<>(Arrays.stream(neg).map(o -> (NeoFeature) o).collect(Collectors.toList()));
	}


	@Override
	public FeatureRevision[] getPos() {
		if (posList != null) {
			pos = posList.stream().toArray(FeatureRevision[]::new);
		} else
		{
			this.posList = new ArrayList<>();
			this.pos = new FeatureRevision[] {};
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
	public Module getModule() {
		return this.module;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		NeoModuleRevision neoModuleRevision = (NeoModuleRevision) o;

		//return Arrays.equals(pos, neoModuleRevision.pos) && Arrays.equals(neg, neoModuleRevision.neg);
		if (this.getPos().length != neoModuleRevision.getPos().length || this.getNeg().length != neoModuleRevision.getNeg().length)
			return false;
		for (int i = 0; i < this.getPos().length; i++) {
			boolean found = false;
			for (int j = 0; j < neoModuleRevision.getPos().length; j++) {
				if (this.getPos()[i].equals(neoModuleRevision.getPos()[j])) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		for (int i = 0; i < this.getNeg().length; i++) {
			boolean found = false;
			for (int j = 0; j < neoModuleRevision.getNeg().length; j++) {
				if (this.getNeg()[i].equals(neoModuleRevision.getNeg()[j])) {
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
		if (this.getPos() != null) {
			for (FeatureRevision featureRevision : this.getPos())
				result += featureRevision.hashCode();
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
		return this.getModuleRevisionString();
	}

}
