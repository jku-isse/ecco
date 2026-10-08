package at.jku.isse.ecco.adapter.python.test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.python.parse.py4j.PY4JParser;

/**
 * Whether the adapter finds a python with libcst and py4j - asked the way the adapter asks
 * ({@link PY4JParser#python()}), so tests run exactly when the adapter can read and write.
 */
final class PythonAvailable {

    static final String NEEDS = "needs a python with the libcst and py4j modules";

    private PythonAvailable() {
    }

    static boolean check() {
        try {
            PY4JParser.python();
            return true;
        } catch (EccoException e) {
            return false;
        }
    }
}
