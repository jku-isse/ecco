package at.jku.isse.ecco.experiment.utils;

import java.util.Collection;

public class CollectionUtils {
    public static <E> E getRandom(Collection<E> e) {
        return e.stream()
                .skip((int) (e.size() * Math.random()))
                .findFirst().get();
    }
}
