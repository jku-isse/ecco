package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.Feature;

import java.util.Collection;

public interface Repository {

	public Collection<? extends Feature> getFeatures();

	public Collection<? extends Association> getAssociations();

}
