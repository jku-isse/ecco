package at.jku.isse.ecco.storage.ser.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SerFeature.hashCode() used to hash on name while equals() compared by id - a genuine
 * equals/hashCode contract violation (name is also mutable via setName()/feature(), unlike id, which
 * has no setter). Feature is used as a HashMap/HashSet key throughout the GUI (FeatureTogglePanel,
 * ConfigurationPickerDialog, FeatureModelTree) and base/.../repository/Repository.java. Traced via
 * git history: equals() was switched from name-based to id-based comparison in the 2016 "major
 * refactoring" commit, at which point getId() was literally implemented as "return this.name" so the
 * two stayed consistent by coincidence; id and name later became independent fields (id immutable
 * after construction, name mutable), but hashCode() was never updated to follow equals().
 */
public class SerFeatureEqualsHashCodeTest {

	@Test
	@Timeout(30)
	public void twoFeaturesWithSameIdButDifferentNameHashToTheSameBucket() {
		SerFeature original = new SerFeature("id1", "Original Name");
		SerFeature renamedCopy = new SerFeature("id1", "Renamed Copy");

		assertTrue(original.equals(renamedCopy), "same id must mean equal, regardless of name");

		Set<SerFeature> set = new HashSet<>();
		set.add(original);

		assertTrue(set.contains(renamedCopy),
				"a HashSet must find an equal (same-id) feature even if its name differs - " +
						"requires hashCode() to be based on the same field as equals()");
	}

	@Test
	@Timeout(30)
	public void renamingAFeatureAlreadyStoredAsAHashSetKeyDoesNotLoseIt() {
		SerFeature feature = new SerFeature("id1", "Original Name");
		Set<SerFeature> set = new HashSet<>();
		set.add(feature);

		feature.setName("Renamed");

		assertTrue(set.contains(feature),
				"renaming a feature must not change its hashCode, or it becomes unreachable in a set it was already inserted into");
	}
}
