package at.jku.isse.ecco.util;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generic set operations.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class SetUtil {

	/**
	 * Returns a new complemented set by removing all the elements from set1 that are also in set 2.
	 *
	 * @param set1
	 *            the set which provides the core elements
	 * @param set2
	 *            the set which contains the elements which will be removed from set 1
	 * @return A new set that is the complement of set1 and set2.
	 */
	public static <T> Set<T> complement(final Set<T> set1, final Set<T> set2) {
		checkNotNull(set1);
		checkNotNull(set2);

		final Set<T> complement = new LinkedHashSet<>();
		complement.addAll(set1);
		complement.removeAll(set2);

		return complement;
	}

	/**
	 * Returns a new intersected set by removing all elements which are not contained in both sets.
	 *
	 * @param set1
	 *            which contains elements
	 * @param set2
	 *            which contains elements
	 * @return A new set that is the intersection of set1 and set2.
	 */
	public static <T> Set<T> intersect(final Set<T> set1, final Set<T> set2) {
		checkNotNull(set1);
		checkNotNull(set2);

		return set1.stream().filter(set2::contains).collect(Collectors.toSet());
	}

	/**
	 * Creates the power set of the given set.
	 *
	 * @param set
	 *            from which a power set should be created
	 * @return The power set of the given elements.
	 */
	public static <T> Set<Set<T>> powerSet(final Set<T> set) {
		checkNotNull(set);

		// add empty set
		Set<Set<T>> powerSet = new LinkedHashSet<>();
		powerSet.add(new LinkedHashSet<>());

		for (final T element : set) {
			final Set<Set<T>> newPowerSet = new LinkedHashSet<>();

			for (final Set<T> subset : powerSet) {
				newPowerSet.add(subset);

				// Make new subset with the current element and the current
				// subset
				final Set<T> newSubset = new LinkedHashSet<>(subset);
				newSubset.add(element);
				newPowerSet.add(newSubset);
			}

			powerSet = newPowerSet;
		}

		return powerSet;
	}

	/**
	 * Returns the union set by combining the unique elements of both sets into a new one.
	 *
	 * @param set1
	 *            which contains elements
	 * @param set2
	 *            which contains elements
	 * @return A new set that is the union of set1 and set2.
	 */
	public static <T> Set<T> union(final Set<T> set1, final Set<T> set2) {
		checkNotNull(set1);
		checkNotNull(set2);

		final Set<T> union = new LinkedHashSet<>();
		union.addAll(set1);
		union.addAll(set2);

		return union;
	}

}
