package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@NodeEntity
public class NeoRemote extends NeoEntity implements Remote  {

    @Property("name")
	private String name;

    @Property("address")
	private String address;

    @Relationship("hasTypeRm")
	private Type type;

	@Relationship("hasFeatureRm")
	private List<Feature> features;

	public NeoRemote() {
		this("", "", Type.LOCAL);
	}

	public NeoRemote(String name, String address, Type type) {
		this.name = name;
		this.address = address;
		this.type = type;
		this.features = new ArrayList<>();
	}


	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getAddress() {
		return this.address;
	}

	@Override
	public void setAddress(String address) {
		this.address = address;
	}

	@Override
	public Type getType() {
		return this.type;
	}

	@Override
	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public Collection<Feature> getFeatures() {
		return this.features;
	}


	@Override
	public String toString() {
		return name + " - " + address + " [" + type + "]";
	}

}
