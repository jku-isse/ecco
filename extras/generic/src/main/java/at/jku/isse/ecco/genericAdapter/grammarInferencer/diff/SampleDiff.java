package at.jku.isse.ecco.genericAdapter.grammarInferencer.diff;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.ParameterSettings;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.mutate.DiffOptimization;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.mutate.GrammarMutatorUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * @author Michael Jahn
 */
public class SampleDiff {

    private diff_match_patch diffMatchPatch;
    private diff_match_patch_list diffMatchPatchList;


    public SampleDiff() {
        diffMatchPatchList = new diff_match_patch_list();

        // settings or optimal results
        diffMatchPatchList.Diff_Timeout = 0;
    }

    public List<Diff> diffSamplesList(List<String> origSample, List<String> compareSample) {
        LinkedList<diff_match_patch_list.Diff> diffList = diffMatchPatchList.diff_main(origSample, compareSample);
        List<Diff> diffValues = new ArrayList<>();

        int curOrigIdx = 0;
        int curCompareIdx = 0;

        Diff prevDiff = null;

//        System.out.print("Compare result: ");
        for (diff_match_patch_list.Diff diff : diffList) {
            Diff newDiff = null;
            switch (diff.operation) {

                case EQUAL:
                    curOrigIdx += origSample.subList(curOrigIdx, origSample.size()).indexOf(diff.textList.get(0));
                    curCompareIdx += compareSample.subList(curCompareIdx, compareSample.size()).indexOf(diff.textList.get(0));
//                    System.out.print("(" + diff.operation + " at: " + curOrigIdx+ " | " + diff.text + ") " );
                    newDiff = new Diff(Diff.Operation.EQUAL, curOrigIdx, diff.textList);

                    curOrigIdx += diff.textList.size();
                    curCompareIdx += diff.textList.size();
                    break;

                case DELETE:
                    curOrigIdx += origSample.subList(curOrigIdx, origSample.size()).indexOf(diff.textList.get(0));

                    // check for replace
                    if (prevDiff != null && prevDiff.getOperation().equals(Diff.Operation.INSERT) && prevDiff.getIndex() == curOrigIdx + 1) {
//                        System.out.print("(REPLACE at: " + curOrigIdx + " | " + diff.text + ") ");
                        diffValues.remove(diffValues.indexOf(prevDiff));
                        newDiff = new Diff(Diff.Operation.REPLACE, curOrigIdx + 1, prevDiff.getValues(), diff.textList);
                    } else {
//                        System.out.print("(" + diff.operation + " at: " + curOrigIdx + " | " + diff.text + ") ");
                        newDiff = new Diff(Diff.Operation.DELETE, curOrigIdx, diff.textList);
                    }

                    curOrigIdx += diff.textList.size();
                    break;

                case INSERT:
                    curCompareIdx += compareSample.subList(curCompareIdx, compareSample.size()).indexOf(diff.textList.get(0));

                    // check for replace
                    if (prevDiff != null && prevDiff.getOperation().equals(Diff.Operation.DELETE) && prevDiff.getIndex() == curOrigIdx - 1) {
//                        System.out.print("(REPLACE at: " + (curOrigIdx - 1) + " | value: " + prevDiff.getValues().get(0) + " with: " + diff.text + ") ");
                        diffValues.remove(diffValues.indexOf(prevDiff));
                        newDiff = new Diff(Diff.Operation.REPLACE, curOrigIdx - 1, prevDiff.getValues(), diff.textList);
                    } else {
                        // check for precending insert diff
                        if(diffValues.size() > 1 && diffValues.get(diffValues.size() - 1).getOperation().equals(Diff.Operation.INSERT)) {
                            List<String> insertValues = new ArrayList<>(diffValues.get(diffValues.size() - 1).getValues());
                            insertValues.addAll(diff.textList);
                            newDiff = new Diff(Diff.Operation.INSERT, curOrigIdx - 1, insertValues);
                            diffValues.remove(diffValues.size() - 1);
                        } else {
//                        System.out.print("(" + diff.operation + " after: " + (curOrigIdx - 1) + " from compareSample at: " + curCompareIdx + " | " + diff.text + ") ");
                            newDiff = new Diff(Diff.Operation.INSERT, curOrigIdx - 1, diff.textList);
                        }
                    }

                    curCompareIdx += diff.textList.size();
                    break;
            }
            diffValues.add(newDiff);
            prevDiff = newDiff;
        }

        // optimize difflist, to avoid the greedy suffix problem
        // if the last equal has a delete or insert with the same beginning tokens, then rotate the diffs to a more logical structure
        if (diffValues.size() >= 2) {
            Diff secondLastDiff = diffValues.get(diffValues.size() - 2);
            Diff lastDiff = diffValues.get(diffValues.size() - 1);

            if (lastDiff.getOperation().equals(Diff.Operation.EQUAL) && secondLastDiff.getValues().size() > 1 &&
                    (secondLastDiff.getOperation().equals(Diff.Operation.DELETE) || secondLastDiff.getOperation().equals(Diff.Operation.INSERT))) {
                // get common prefix of both diffs
                int idx = 0;
                while (secondLastDiff.getValues().size() > idx && lastDiff.getValues().size() > idx &&
                        secondLastDiff.getValues().get(idx).equals(lastDiff.getValues().get(idx))) {
                    idx++;
                }
                if (idx > 0) {

                    //rotate diffs
                    Diff newEqualsDiff = new Diff(Diff.Operation.EQUAL, secondLastDiff.getIndex(), secondLastDiff.getValues().subList(0, idx));
                    List<String> newSecondLastDiffValue = new ArrayList<>(secondLastDiff.getValues().subList(idx, secondLastDiff.getValues().size()));
                    newSecondLastDiffValue.addAll(new ArrayList<>(newEqualsDiff.getValues()));
                    Diff newSecondLastDiff = new Diff(secondLastDiff.getOperation(),
                            newEqualsDiff.getIndex() + newEqualsDiff.getValues().size(),
                            newSecondLastDiffValue);
                    Diff newLastDiff = new Diff(Diff.Operation.EQUAL, newSecondLastDiff.getIndex() + newSecondLastDiff.getValues().size(),
                            new ArrayList<>(lastDiff.getValues().subList(idx, lastDiff.getValues().size())));

                    // remove two last elements
                    diffValues.remove(diffValues.size() - 1);

                    diffValues.remove(diffValues.size() - 1);

                    // add new elements
                    diffValues.add(newEqualsDiff);
                    diffValues.add(newSecondLastDiff);
                    diffValues.add(newLastDiff);
                    if(ParameterSettings.DEBUG_OUTPUT) System.out.println("Hint: Optimized diff list, rotated last three elements!");
                    if(ParameterSettings.DEBUG_OUTPUT)System.out.println("previous last two elements:");
                    if(ParameterSettings.DEBUG_OUTPUT)System.out.println("(" + secondLastDiff.getOperation() + " at: " + secondLastDiff.getIndex() + " values: " + secondLastDiff.getValuesString() + ")");
                    if(ParameterSettings.DEBUG_OUTPUT)System.out.println("(" + lastDiff.getOperation() + " at: " + lastDiff.getIndex() + " values: " + lastDiff.getValuesString());
                }
            }
        }
        return diffValues;

    }

    public void diffSamplesString(String origSample, String testSample)
    {
        diffMatchPatch = new diff_match_patch();
        List<diff_match_patch.Diff> diff = diffMatchPatch.diff_main(origSample, testSample);

    }

    /**
     * Computes and returns the levensthein distance between the two samples
     * Computation is based on the results of the {@link this.diffSamplesList} method
     *
     * @param origSample
     * @param compareSample
     * @param ignoreRepeatingPatterns
     * @return
     */
    public int computeLevenshteinDistance(List<String> origSample, List<String> compareSample, boolean ignoreRepeatingPatterns) {

        List<Diff> diffList = diffSamplesList(origSample, compareSample);
        if(ParameterSettings.USE_OPTIMIZED_DIFF_FOR_DISTANCE) {
            diffList = new DiffOptimization().optimizeDiffList(diffList);
        }

        if(ignoreRepeatingPatterns) {
            for (Diff diff : diffList) {
                // TODO implement this!

                if(!diff.getOperation().equals(Diff.Operation.EQUAL)) {
                    List<GrammarMutatorUtils.RepeatingPattern> replacePattern = GrammarMutatorUtils.computeShortestRepeatedPatterns(diff.getReplaceValues(), false);
                    diff.setReplaceValues(deleteRepeatingPatternValues(diff.getReplaceValues(), replacePattern));
                    List<GrammarMutatorUtils.RepeatingPattern> valuePattern = GrammarMutatorUtils.computeShortestRepeatedPatterns(diff.getValues(), false);
                    diff.setValues(deleteRepeatingPatternValues(diff.getValues(), valuePattern));

                }
            }
        }

        int levensthein = 0;
        for (Diff diff : diffList) {
            switch (diff.getOperation()) {
                case REPLACE:
                    levensthein+=Math.max(diff.getReplaceValues().size(), diff.getValues().size());
                    break;
                case INSERT:
                    levensthein += diff.getValues().size();
                    break;
                case DELETE:
                    levensthein += diff.getValues().size();
                    break;
            }
        }

        return levensthein;
    }

    private List<String> deleteRepeatingPatternValues(List<String> diffValues, List<GrammarMutatorUtils.RepeatingPattern> patternList) {
        patternList = patternList.stream().filter(pattern -> pattern.getNrRepetitions() > ParameterSettings.MIN_NR_REPETITIONS).collect(toList());
        for (GrammarMutatorUtils.RepeatingPattern pattern : patternList) {
            List<String> deleteValues = diffValues.subList(pattern.getStartIdx() + pattern.getPattern().size(), pattern.getStartIdx() + pattern.getPattern().size() * pattern.getNrRepetitions());
            for (int i = 0; i < deleteValues.size(); i++) {
                deleteValues.set(i, null);
            }
        }
        return diffValues.stream().filter(value -> value != null).collect(toList());
    }

    public int computeCommongBeginningCharacters(List<String> processedSample, List<String> sampleTokenized) {
        int curIdx = 0;
        while(processedSample.size() > curIdx && sampleTokenized.size() > curIdx &&
                processedSample.get(curIdx).equals(sampleTokenized.get(curIdx))) {
            curIdx++;
        }
        return curIdx;
    }

    public int computeNrOfMutationDiffs(List<Diff> sampleDiff) {
        int result = 0;
        for (Diff diff : sampleDiff) {
            if(!diff.getOperation().equals(Diff.Operation.EQUAL)) {
                result++;
            }
        }
        return result;
    }
}
