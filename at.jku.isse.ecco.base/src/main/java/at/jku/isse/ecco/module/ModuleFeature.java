package at.jku.isse.ecco.module;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;

import java.util.Collection;
import java.util.Set;

public interface ModuleFeature extends Persistable, Set<FeatureVersion>, Iterable<FeatureVersion>, Collection<FeatureVersion> {

	public Feature getFeature();

	public boolean getSign();

}
