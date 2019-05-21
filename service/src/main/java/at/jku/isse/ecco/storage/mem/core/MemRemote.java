package at.jku.isse.ecco.storage.mem.core;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;

import java.util.ArrayList;
import java.util.Collection;

public class MemRemote implements Remote {

	public static final long serialVersionUID = 1L;


	private String name;
	private String address;
	private Type type;

	private Collection<Feature> features;


	public MemRemote() {
		this("", "", Type.LOCAL);
	}

	public MemRemote(String name, String address, Type type) {
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
