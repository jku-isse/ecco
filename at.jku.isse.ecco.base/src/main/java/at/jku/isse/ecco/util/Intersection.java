package at.jku.isse.ecco.util;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A DTO for intersections.
 *
 * @param <T> type of the intersected objects
 * @author Hannes Thaller
 * @version 1.0
 */
public class Intersection<T> {
    public final T left, right, intersection;

    /**
     * Constructs a new intersection with the given left, right and intersection
     * object.
     *  @param left         side of the intersection
     * @param intersection it self
     * @param right        side of the intersection
     */
    public Intersection(final T left, final T intersection, final T right) {
        checkNotNull(left);
        checkNotNull(right);
        checkNotNull(intersection);

        this.left = left;
        this.right = right;
        this.intersection = intersection;
    }
}
