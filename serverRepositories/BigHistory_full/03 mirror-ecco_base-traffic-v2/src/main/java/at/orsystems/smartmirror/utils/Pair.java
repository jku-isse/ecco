package at.orsystems.smartmirror.utils;

import org.springframework.lang.NonNull;

import java.util.Objects;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */
public class Pair<L, R> {
    @NonNull
    public final L left;
    @NonNull
    public final R right;

    public Pair(L left, R right) {
        this.left = Objects.requireNonNull(left);
        this.right = Objects.requireNonNull(right);
    }
}
