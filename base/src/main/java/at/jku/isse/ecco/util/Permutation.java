package at.jku.isse.ecco.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Permutation {
    public static <T> Collection<List<T>> generatePermutations(Collection<T> collection) {
        List<T> list = new ArrayList<>(collection); // Convert the collection to a list
        List<List<T>> result = new ArrayList<>();
        permute(list, 0, result);  // Call the helper method to generate permutations
        return result;
    }

    // Helper method to recursively generate permutations
    private static <T> void permute(List<T> list, int start, List<List<T>> result) {
        if (start == list.size() - 1) {
            result.add(new ArrayList<>(list)); // Add the current permutation to the result
        } else {
            for (int i = start; i < list.size(); i++) {
                Collections.swap(list, start, i);  // Swap current element with the start
                permute(list, start + 1, result);  // Recurse on the remaining sublist
                Collections.swap(list, start, i);  // Backtrack by undoing the swap
            }
        }
    }
}
