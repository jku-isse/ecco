package at.jku.isse.ecco.java.test;

import java.util.Optional;

public class Looptest {

    private void test() {
        {
            int i = 0;
            while (i < 10)
                i++;
        }

        do {
            Optional.ofNullable(null).ifPresent(System.out::println);
        } while (a < b);
    }
}
