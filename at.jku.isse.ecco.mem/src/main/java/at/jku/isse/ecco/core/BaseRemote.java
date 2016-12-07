package at.jku.isse.ecco.core;

import at.jku.isse.ecco.feature.Feature;

import java.util.Collection;

public class BaseRemote implements Remote {

	private String name;
	private String address;
	private Type type;


	public BaseRemote() {
		this.name = "";
		this.address = "";
		this.type = Type.LOCAL;
	}

	public BaseRemote(String name, String address, Type type) {
		this.name = name;
		this.address = address;
		this.type = type;
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
		return null; // TODO
	}

}
