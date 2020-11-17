package at.jku.isse.ecco.adapter.java.test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class Simple {

    private String i = "Test";
    private String o;

    public Simple(String s) throws IllegalArgumentException {
        super();
        o = s;
    }

    public Simple() {
        super();
    }

    public <T, R extends Comparable<T>> void test(List<Simple> uneedded, T... f) throws Exception {
        final Supplier<String> a = (Serializable & Supplier<String>) () -> "TEST";
        Function<String, String> c = b -> b;
        synchronized (a) {
            for (int i = 0, y = 2; i < y; i += 2, y++) {
                new ArrayList<String>(1) {

                };
                try {
                    int ii = super.hashCode();
                } catch (IllegalArgumentException | IllegalStateException e) {
                    throw e;
                }
                String[] test1 = new String[]{};
                String[] test2 = new String[0];
            }
        }

    }
}