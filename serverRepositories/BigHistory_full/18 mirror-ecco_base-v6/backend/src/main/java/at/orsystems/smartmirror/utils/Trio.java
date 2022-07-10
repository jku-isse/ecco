package at.orsystems.smartmirror.utils;

import org.springframework.lang.NonNull;

import static java.util.Objects.requireNonNull;

/**
 * @author Michael Ratzenböck
 * @since 2020
 */
public class Trio<F, S, T> {
    @NonNull
    public final F first;
    @NonNull
    public final S second;
    @NonNull
    public final T third;

    public Trio(F first, S second, T third) {
        this.first = requireNonNull(first);
        this.second = requireNonNull(second);
        this.third = requireNonNull(third);
    }
}
