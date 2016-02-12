package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;

import java.util.Optional;
import java.util.Set;

public class JpaFeatureDao implements FeatureDao {

	@Override
	public boolean exists(String featureName) {
		return false;
	}

	@Override
	public Optional<FeatureVersion> find(String featureName, int version) {
		return null;
	}

	@Override
	public Set<Feature> loadAllFeatures() {
		return null;
	}

	@Override
	public Optional<Set<FeatureVersion>> loadAllVersions(String featureName) {
		return null;
	}

	@Override
	public Set<String> loadAllFeatureNames() {
		return null;
	}

	@Override
	public void removeWithAllVersions(String featureName) {

	}

	@Override
	public void open() {

	}

	@Override
	public void close() {

	}

	@Override
	public void init() {

	}

	@Override
	public Feature load(String id) {
		return null;
	}

	@Override
	public void remove(String id) {

	}

	@Override
	public void remove(Feature entity) {

	}

	@Override
	public Feature save(Feature entity) {
		return null;
	}
}
