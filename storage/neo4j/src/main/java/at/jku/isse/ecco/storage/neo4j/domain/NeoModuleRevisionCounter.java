package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

import static com.google.common.base.Preconditions.checkNotNull;

@NodeEntity
public class NeoModuleRevisionCounter extends NeoEntity implements ModuleRevisionCounter {

	// no incoming from mR
    @Relationship("hasModuleRevisionRvCt")
	private NeoModuleRevision moduleRevision;

    @Property("count")
	private int count;

    public NeoModuleRevisionCounter () {}

	public NeoModuleRevisionCounter(NeoModuleRevision moduleRevision) {
		checkNotNull(moduleRevision);
		this.moduleRevision = moduleRevision;
		this.count = 0;
	}


	@Override
	public NeoModuleRevision getObject() {
		return this.moduleRevision;
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
	public String toString() {
		return this.getModuleRevisionCounterString();
	}

}
