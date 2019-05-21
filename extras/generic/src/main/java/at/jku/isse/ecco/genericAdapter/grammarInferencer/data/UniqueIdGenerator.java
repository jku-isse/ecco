package at.jku.isse.ecco.genericAdapter.grammarInferencer.data;

/**
 * @author Michael Jahn
 */
public class UniqueIdGenerator {

    private static int nextId = 0;

    public static int getNextId() {
        return nextId++;
    }

    public static void resetId() {
        nextId = 0;
    }
}
