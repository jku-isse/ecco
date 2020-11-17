package at.jku.isse.ecco.core;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Feature;

import java.util.Collection;

public interface Remote extends Persistable {

	public enum Type {
		LOCAL, REMOTE;
	}


	public String getName();

	public void setName(String name);


	public String getAddress();

	public void setAddress(String address);


	public Type getType();

	public void setType(Type type);


	public Collection<Feature> getFeatures();

}
