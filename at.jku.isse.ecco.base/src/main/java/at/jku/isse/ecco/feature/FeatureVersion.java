package at.jku.isse.ecco.feature;

import at.jku.isse.ecco.dao.Persistable;

public interface FeatureVersion extends Persistable {

	public static final int NEWEST = -1;
	public static final int ANY = -2;

	public Feature getFeature();

	public int getVersion();

	public String getDescription();

	public void setDescription(String description);

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object object);

}
