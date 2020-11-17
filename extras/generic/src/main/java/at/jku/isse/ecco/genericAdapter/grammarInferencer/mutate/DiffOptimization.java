package at.jku.isse.ecco.genericAdapter.grammarInferencer.mutate;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.diff.Diff;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.diff.DiffUtils;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.ParameterSettings;

import java.util.ArrayList;
import java.util.List;

public class DiffOptimization {

    public DiffOptimization() {
    }

    public List<Diff> optimizeDiffList(List<Diff> diffList) {
        List<Diff> optimizedDiffList = DiffUtils.getDeepCopy(diffList);

        optimizedDiffList = performInsertEqualsOptimization(optimizedDiffList);
//        optimizedDiffList = optimizeDeleteEqualTokens(optimizedDiffList);

        if (ParameterSettings.SUMMARIZE_ALL_BETWEEN_TWO_EQUALS) {
            optimizedDiffList = summarizeBetweenEquals(optimizedDiffList);
        }

        optimizedDiffList = performSummarization2(optimizedDiffList);
//        optimizedDiffList = performSummarization(optimizedDiffList);
        optimizedDiffList = mergeOperations(optimizedDiffList);


        if (ParameterSettings.DEBUG_OUTPUT) {
            System.out.println("Diff after optimization: ");
            printDiff(optimizedDiffList);
        }

        // check if optimization produced an incorrect diff list
        List<Diff> origDiff = generateFallbackDiff(diffList);
        List<Diff> newDiff = generateFallbackDiff(optimizedDiffList);
        if (origDiff.size() != newDiff.size()) {
            if(ParameterSettings.INFO_OUTPUT)
                System.out.println("-------------------------------ATTENTION!!! Inconsistency during optimization detected!----------------------------");
        } else {
            for (int i = 0; i < origDiff.size(); i++) {
                if (!origDiff.get(i).equals(newDiff.get(i))) {
                    if(ParameterSettings.INFO_OUTPUT) {
                        System.out.println("-------------------------------ATTENTION!!! Inconsistency during optimization detected!----------------------------");
                        System.out.print("Orig diff: ");
                        DiffUtils.printSingleDiff(origDiff.get(i));
                        System.out.print("New  diff: ");
                        DiffUtils.printSingleDiff(newDiff.get(i));
                    }
                }
            }
        }
        return optimizedDiffList;
    }

    /**
     * Searches for constructs like
     *
     * (EQUAL at: 3 values: "(STRING")
     * (INSERT at: 5 values: ",(REF)")
     * (EQUAL at: 9 values: ",REF,.F.);")
     *
     * and changes them to
     *
     * (EQUAL at: 3 values: "(STRING,")
     * (INSERT at: 5 values: "(REF),")
     * (EQUAL at: 9 values: "REF,.F.);")
     *
     * @param diffList
     * @return
     */
    private List<Diff> performInsertEqualsOptimization(List<Diff> diffList) {

        Diff prevDiff = null;
        int idx = 0;
        List<Diff> diffsToBeRemoved = new ArrayList<>();
        for (Diff diff : diffList) {

            // search for insert operation between two equals
            if (diff.getOperation().equals(Diff.Operation.INSERT)
                    && prevDiff != null && prevDiff.getOperation().equals(Diff.Operation.EQUAL)
                    && idx + 1 < diffList.size()
                    && diffList.get(idx + 1).getOperation().equals(Diff.Operation.EQUAL)) {

                Diff firstEquals = prevDiff;
                Diff insert = diff;
                Diff secondEquals = diffList.get(idx + 1);

                // insert and the preceding equals must start with the same token
                if (insert.getValues().size() > 0 && secondEquals.getValues().size() > 0 &&
                        insert.getValues().get(0).equals(secondEquals.getValues().get(0))) {

                    int curMatchingCount = 1;
                    while (insert.getValues().size() >= curMatchingCount
                            && secondEquals.getValues().size() >= curMatchingCount
                            && insert.getValues().get(curMatchingCount - 1).equals(secondEquals.getValues().get(curMatchingCount - 1))) {
                        curMatchingCount++;
                    }
                    curMatchingCount--;

                    // change values
                    firstEquals.getValues().addAll(insert.getValues().subList(0, curMatchingCount));
                    insert.getValues().addAll(insert.getValues().subList(0, curMatchingCount));
                    for (int i = 0; i < curMatchingCount; i++) {
                        insert.getValues().remove(0);
                        secondEquals.getValues().remove(0);
                    }

                    if (secondEquals.getValues().size() <= 0) {
                        diffsToBeRemoved.add(secondEquals);
                    }

                    if(ParameterSettings.DEBUG_OUTPUT) System.out.println("INFO: performed insertEquals optimization!");
                }
            }

            prevDiff = diff;
            idx++;
        }

        diffList.removeAll(diffsToBeRemoved);

        return diffList;
    }

    /**
     * Summarizes all mutation operations between two equals
     */
    private List<Diff> summarizeBetweenEquals(List<Diff> diffList) {

        List<Diff> summarizedDiffList = new ArrayList<>();

        Diff lastInsertSummDiff = null;
        Diff prevDiff = null;
        for (Diff diff : diffList) {
            if (prevDiff != null && !diff.getOperation().equals(Diff.Operation.EQUAL) && !prevDiff.getOperation().equals(Diff.Operation.EQUAL)) {

                if (prevDiff.getOperation().equals(Diff.Operation.DELETE)) {
                    prevDiff.getReplaceValues().addAll(prevDiff.getValues());
                    prevDiff.getValues().clear();
                    prevDiff.setOperation(Diff.Operation.REPLACE);
                }

                if (lastInsertSummDiff.getOperation().equals(Diff.Operation.INSERT) && diff.getOperation().equals(Diff.Operation.INSERT)) {
                    lastInsertSummDiff.getValues().addAll(diff.getValues());
                } else if (diff.getOperation().equals(Diff.Operation.DELETE)) {
                    lastInsertSummDiff.setOperation(Diff.Operation.REPLACE);
                    lastInsertSummDiff.getReplaceValues().addAll(diff.getValues());
                } else {
                    lastInsertSummDiff.setOperation(Diff.Operation.REPLACE);
                    lastInsertSummDiff.getReplaceValues().addAll(diff.getReplaceValues());
                    lastInsertSummDiff.getValues().addAll(diff.getValues());
                }
            } else {
                summarizedDiffList.add(diff);
                lastInsertSummDiff = diff;
            }

            prevDiff = diff;
        }
        return summarizedDiffList;
    }

    /**
     * TODO [OPTIMIZATION] NOT USED currently, as it is error prone
     * <p>
     * Searches for constructs like
     * <p>
     * (EQUAL at: 3 values: "(STRING")
     * (DELETE at: 5 values: ",(REF)")
     * (EQUAL at: 9 values: ",REF,.F.);")
     * <p>
     * and changes them to
     * <p>
     * (EQUAL at: 3 values: "(STRING,")
     * (DELETE at: 5 values: "(REF),")
     * (EQUAL at: 9 values: "REF,.F.);")
     *
     * @param diffList
     * @return
     */
    private List<Diff> optimizeDeleteEqualTokens(List<Diff> diffList) {

        Diff prevDiff = null;
        int idx = 0;
        for (Diff diff : diffList) {

            // search for delete operation between two equals
            if (diff.getOperation().equals(Diff.Operation.DELETE)
                    && prevDiff != null && prevDiff.getOperation().equals(Diff.Operation.EQUAL)
                    && idx + 1 < diffList.size()
                    && diffList.get(idx + 1).getOperation().equals(Diff.Operation.EQUAL)) {

                Diff firstEquals = prevDiff;
                Diff delete = diff;
                Diff secondEquals = diffList.get(idx + 1);

                // delete and the preceding equals must start with the same token
                if (delete.getValues().get(0).equals(secondEquals.getValues().get(0))) {

                    // change values
                    firstEquals.getValues().add(delete.getValues().get(0));
                    delete.getValues().add(delete.getValues().remove(0));
                    secondEquals.getValues().remove(0);

                    System.out.println("INFO: performed DeleteEquals optimization!");
                }
            }

            prevDiff = diff;
            idx++;
        }

        return diffList;
    }

    private List<Diff> performSummarization2(List<Diff> diffList) {

        List<List<Diff>> sumLists = new ArrayList<>();

        int idx = 0;
        int count = 0;
        Diff prevDiff = null;
        for (Diff diff : diffList) {
            if (prevDiff != null) {
                if (diff.getIndex() - prevDiff.getIndex() <= ParameterSettings.MIN_SUMMARIZATION_DISTANCE) {

                    if (ParameterSettings.INCLUDE_EQUALS_IN_COUNT || !diff.getOperation().equals(Diff.Operation.EQUAL)) {
                        count++;
                    }

                } else {
                    if (count >= ParameterSettings.MIN_SUMMARIZATION_COUNT - 1) {
                        // gather diffs for summarization
                        sumLists.add(new ArrayList<>(diffList.subList(idx - count - 1, idx - 1)));
                    }
                    count = 0;
                }
            }
            prevDiff = diff;
            idx++;
        }

        // postprocess last elements
        if (count > ParameterSettings.MIN_SUMMARIZATION_COUNT) {
            sumLists.add(new ArrayList<>(diffList.subList(idx - count - 1, idx - 1)));
        }

        // postprocess sumLists
        for (List<Diff> sumList : sumLists) {

            // optimization: extend sumList until an equal diff is found
            int lastSumIdx = diffList.indexOf(sumList.get(sumList.size() - 1)) + 1;
            while (lastSumIdx < diffList.size() && !diffList.get(lastSumIdx).getOperation().equals(Diff.Operation.EQUAL)) {
                sumList.add(diffList.get(lastSumIdx));
                lastSumIdx++;
            }

            // remove pre and post equal diffs
            if (sumList.get(0).getOperation().equals(Diff.Operation.EQUAL)) {
                sumList.remove(0);
            }
            if (sumList.get(sumList.size() - 1).getOperation().equals(Diff.Operation.EQUAL)) {
                sumList.remove(sumList.size() - 1);
            }
            if (sumList.size() >= ParameterSettings.MIN_SUMMARIZATION_COUNT) {

                int sumListIdx = 0;
                int diffListIdx = -1;
                while (diffListIdx == -1 && sumListIdx < sumList.size()) {
                    diffListIdx = diffList.indexOf(sumList.get(sumListIdx));
                    sumListIdx++;
                }
                if (diffListIdx > -1) {

                    // delete already merged diffs
                    sumList = sumList.subList(sumListIdx - 1, sumList.size());

                    // apply changes to diffList
                    List<String> replaceValues = new ArrayList<>();
                    List<java.lang.String> insertValues = new ArrayList<>();
                    for (Diff diff : sumList) {

                        // insert values
                        if (!diff.getOperation().equals(Diff.Operation.DELETE)) {
                            insertValues.addAll(diff.getValues());
                        }

                        // replace values
                        if (diff.getOperation().equals(Diff.Operation.EQUAL)) {
                            replaceValues.addAll(diff.getValues());
                        } else if (diff.getOperation().equals(Diff.Operation.REPLACE)) {
                            replaceValues.addAll(diff.getReplaceValues());
                        } else if (diff.getOperation().equals(Diff.Operation.DELETE)) {
                            replaceValues.addAll(diff.getValues());
                        }
                    }

                    // due to the optimization it can happen that replace and insert values share a common prefix
                    // this must be removed as a parser with only 1 LA token would not be able to resolve this
                    int prefixIdx = 0;
                    if (replaceValues.size() > 0 && insertValues.size() > 0) {
                        while (replaceValues.size() > prefixIdx && insertValues.size() > prefixIdx && replaceValues.get(prefixIdx).equals(insertValues.get(prefixIdx))) {
                            prefixIdx++;
                        }
                    }
                    if (prefixIdx > 0) {
                        diffList.add(diffListIdx, new Diff(Diff.Operation.EQUAL, sumList.get(0).getIndex() - prefixIdx, replaceValues.subList(0, prefixIdx)));
                        diffListIdx++;
                        replaceValues = replaceValues.subList(prefixIdx, replaceValues.size());
                        insertValues = insertValues.subList(prefixIdx, insertValues.size());
                    }

                    diffList.add(diffListIdx, new Diff(Diff.Operation.REPLACE, sumList.get(0).getIndex(), replaceValues, insertValues));
                    diffList.removeAll(sumList);
                }
            }
        }

        return diffList;
    }

    private List<Diff> performSummarization(List<Diff> diffList) {
        java.util.List<Diff> sumCandidates = new java.util.ArrayList<>();
        Diff prevDiff = null;

        java.util.List<java.util.List<Diff>> sumResults = new java.util.ArrayList<>();

        // gather possible candidates
        for (Diff diff : diffList) {
            if (prevDiff != null && diff.getIndex() - prevDiff.getIndex() <= ParameterSettings.MIN_SUMMARIZATION_DISTANCE) {
                if (!sumCandidates.contains(prevDiff) && !prevDiff.getOperation().equals(Diff.Operation.EQUAL)) {
                    sumCandidates.add(prevDiff);
                }
                sumCandidates.add(diff);
            }
            prevDiff = diff;
        }

        // find valid results
        Diff prevCand = null;
        int count = 0;
        int curIdx = 0;
        for (Diff diff : sumCandidates) {
            if (prevCand != null) {

                if (diff.getIndex() - prevCand.getIndex() <= ParameterSettings.MIN_SUMMARIZATION_DISTANCE) {
                    count++;
                } else if (count >= ParameterSettings.MIN_SUMMARIZATION_COUNT - 1) {
                    java.util.List<Diff> sumResult = new java.util.ArrayList<>(sumCandidates.subList(curIdx - count - 1, curIdx));

                    // special optimization: also include subsequent diffs, until an equal diff is found
                    int idx = diffList.indexOf(sumResult.get(sumResult.size() - 1)) + 1;
                    while (idx < diffList.size() && !diffList.get(idx).getOperation().equals(Diff.Operation.EQUAL)) {
                        sumResult.add(diffList.get(idx));
                        idx++;
                    }

                    // remove pre and post equal diffs
                    if (sumResult.get(0).getOperation().equals(Diff.Operation.EQUAL)) {
                        sumResult.remove(0);
                    }
                    if (sumResult.get(sumResult.size() - 1).getOperation().equals(Diff.Operation.EQUAL)) {
                        sumResult.remove(sumResult.size() - 1);
                    }
                    if (sumResult.size() >= ParameterSettings.MIN_SUMMARIZATION_COUNT) {
                        sumResults.add(sumResult);
                    }
                    count = 0;
                }
            }
            curIdx++;
            prevCand = diff;
        }

        // postprocess last elements
        if (count >= ParameterSettings.MIN_SUMMARIZATION_COUNT - 1) {

            java.util.List<Diff> sumResult = sumCandidates.subList(curIdx - count, curIdx);

            // special optimization: also include subsequent diffs, until an equal diff is found
            int idx = diffList.indexOf(sumResult.get(sumResult.size() - 1)) + 1;
            while (idx < diffList.size() && !diffList.get(idx).getOperation().equals(Diff.Operation.EQUAL)) {
                sumResult.add(diffList.get(idx));
                idx++;
            }

            // remove pre and post equal diffs

            if (sumResult.get(0).getOperation().equals(Diff.Operation.EQUAL)) {
                sumResult.remove(0);
            }
            if (sumResult.size() > 0 && sumResult.get(sumResult.size() - 1).getOperation().equals(Diff.Operation.EQUAL)) {
                sumResult.remove(sumResult.size() - 1);
            }
            if (sumResult.size() >= ParameterSettings.MIN_SUMMARIZATION_COUNT) {
                sumResults.add(sumResult);
            }
        }

        for (java.util.List<Diff> sumResult : sumResults) {


            System.out.println("OPTIMIZATION: Found diff elements to merge: ");
            sumResult.forEach((diff) -> System.out.print("at: " + diff.getIndex() + " " + diff.getValues() + " "));
            System.out.println();

            // apply results to diffList
            int diffListIdx = diffList.indexOf(sumResult.get(0));


            if (diffListIdx != -1) {
                java.util.List<java.lang.String> replaceValues = new java.util.ArrayList<>();
                java.util.List<java.lang.String> insertValues = new java.util.ArrayList<>();
                for (Diff diff : sumResult) {

                    // insert values
                    if (!diff.getOperation().equals(Diff.Operation.DELETE)) {
                        insertValues.addAll(diff.getValues());
                    }

                    // replace values
                    if (diff.getOperation().equals(Diff.Operation.EQUAL)) {
                        replaceValues.addAll(diff.getValues());
                    } else if (diff.getOperation().equals(Diff.Operation.REPLACE)) {
                        replaceValues.addAll(diff.getReplaceValues());
                    } else if (diff.getOperation().equals(Diff.Operation.DELETE)) {
                        replaceValues.addAll(diff.getValues());
                    }
                }

                // due to the optimization it can happen that replace and insert values share a common prefix
                // this must be removed as a parser with only 1 LA token would not be able to resolve this
                int prefixIdx = 0;
                if (replaceValues.size() > 0 && insertValues.size() > 0) {
                    while (replaceValues.size() > prefixIdx && insertValues.size() > prefixIdx && replaceValues.get(prefixIdx).equals(insertValues.get(prefixIdx))) {
                        prefixIdx++;
                    }
                }
                if (prefixIdx > 0) {
                    diffList.add(diffListIdx, new Diff(Diff.Operation.EQUAL, sumResult.get(0).getIndex() - prefixIdx, replaceValues.subList(0, prefixIdx)));
                    diffListIdx++;
                    replaceValues = replaceValues.subList(prefixIdx, replaceValues.size());
                    insertValues = insertValues.subList(prefixIdx, insertValues.size());
                }

                diffList.add(diffListIdx, new Diff(Diff.Operation.REPLACE, sumResult.get(0).getIndex(), replaceValues, insertValues));
                diffList.removeAll(sumResult);
            }
        }

        return diffList;
    }

    private List<Diff> mergeOperations(List<Diff> diffList) {

        Diff prevDiff = null;
        List<Diff> removeDiffs = new ArrayList<>();
        boolean removedDiff = false;
        for (Diff diff : diffList) {

            if (prevDiff != null && prevDiff.getOperation().equals(diff.getOperation())) {

                switch (diff.getOperation()) {
                    case REPLACE:
                        prevDiff.getValues().addAll(diff.getValues());
                        prevDiff.getReplaceValues().addAll(diff.getValues());
                        break;
                    case EQUAL:
                    case INSERT:
                    case DELETE:
                        prevDiff.getValues().addAll(diff.getValues());
                        break;
                }
                removeDiffs.add(diff);
                removedDiff = true;
            } else {
                removedDiff = false;
            }
            if (!removedDiff) {
                prevDiff = diff;
                removedDiff = false;
            }

        }

        diffList.removeAll(removeDiffs);

        return diffList;
    }

    public void printDiff(List<Diff> testSampleTokenizedDiff) {
        for (Diff diff : testSampleTokenizedDiff) {
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
        System.out.println("\n");
    }

    /**
     * merge all diffs to one replace, except the first prefix equals
     *
     * @param diffList
     */
    public List<Diff> generateFallbackDiff(List<Diff> diffList) {
        List<Diff> fallbackDiff = DiffUtils.getDeepCopy(diffList);

        Diff mergeDiff = null;

        int idx = 0;
        for (Diff diff : fallbackDiff) {

            if (mergeDiff != null) {
                switch (diff.getOperation()) {
                    case EQUAL:
                        mergeDiff.getReplaceValues().addAll(diff.getValues());
                        mergeDiff.getValues().addAll(diff.getValues());
                        break;
                    case REPLACE:
                        mergeDiff.getReplaceValues().addAll(diff.getReplaceValues());
                        mergeDiff.getValues().addAll(diff.getValues());
                        break;
                    case DELETE:
                        mergeDiff.getReplaceValues().addAll(diff.getValues());
                        break;
                    case INSERT:
                        mergeDiff.getValues().addAll(diff.getValues());
                }

            } else if (!diff.getOperation().equals(Diff.Operation.EQUAL)) {
                mergeDiff = diff;

                if (mergeDiff.getReplaceValues() == null) {
                    mergeDiff.setReplaceValues(new ArrayList<>());
                }

                if (mergeDiff.getOperation().equals(Diff.Operation.DELETE)) {
                    mergeDiff.getReplaceValues().addAll(mergeDiff.getValues());
                    mergeDiff.getValues().clear();
                }
                mergeDiff.setOperation(Diff.Operation.REPLACE);


            }
            idx++;
        }

        int removeIdx = fallbackDiff.indexOf(mergeDiff);
        while (fallbackDiff.size() > removeIdx + 1) {
            fallbackDiff.remove(fallbackDiff.size() - 1);
        }

        return fallbackDiff;
    }
}