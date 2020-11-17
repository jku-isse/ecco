package at.jku.isse.ecco.genericAdapter.grammarInferencer.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Michael Jahn
 */
public class DiffUtils {

    public static void printSingleDiff(Diff diff) {

        System.out.print("(" + diff.getOperation() + " at: " + diff.getIndex());
        if (diff.getReplaceValues() != null) {
            System.out.print(" replaced: \"");
            diff.getReplaceValues().forEach(System.out::print);
            System.out.print("\"");
        }
        System.out.print(" values: \"");
        diff.getValues().forEach(System.out::print);
        System.out.println("\")");

    }

    public static void printDiff(List<Diff> testSampleTokenizedDiff) {
        System.out.print("Diff Result: \n");
        testSampleTokenizedDiff.forEach(DiffUtils::printSingleDiff);
        System.out.println("\n");
    }

    public static List<Diff> getDeepCopy(List<Diff> diffList) {
        List<Diff> newDiffList = new ArrayList<>();

        for (Diff diff : diffList) {
            if (diff.getReplaceValues() != null) {
                newDiffList.add(new Diff(diff.getOperation(), diff.getIndex(), new ArrayList<>(diff.getReplaceValues()), new ArrayList<>(diff.getValues())));
            } else {
                newDiffList.add(new Diff(diff.getOperation(), diff.getIndex(), new ArrayList<>(diff.getValues())));
            }
        }
        return newDiffList;
    }
}
