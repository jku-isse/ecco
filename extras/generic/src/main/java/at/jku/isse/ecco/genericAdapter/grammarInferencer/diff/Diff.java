package at.jku.isse.ecco.genericAdapter.grammarInferencer.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Michael Jahn
 */
public class Diff {


    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public Diff getDeepCopy() {
        return replaceValues == null ? new Diff(operation, index, new ArrayList<>(values)) : new Diff(operation, index, new ArrayList<>(replaceValues), new ArrayList<>(values));
    }

    public void setIndex(int index) {
        this.index = index;
    }


    public enum Operation {
        REPLACE,
        INSERT,
        DELETE,
        EQUAL
    }

    private Operation operation;
    private int index;
    private List<String> replaceValues;
    private List<String> values;

    public Diff(Operation operation, int index, List<String> values) {
        this.operation = operation;
        this.index = index;
        this.replaceValues = new ArrayList<>();
        this.values = new ArrayList<>();
        this.values.addAll(values);
    }

    public Diff(Operation operation, int index, List<String> replaceValues, List<String> values) {
        this.operation = operation;
        this.index = index;
        this.replaceValues = replaceValues;
        this.values = new ArrayList<>();
        this.values.addAll(values);
    }

    public int getIndex() {
        return index;
    }

    public Operation getOperation() {
        return operation;
    }

    public List<String> getReplaceValues() {
        return replaceValues;
    }

    public List<String> getValues() {
        return values;
    }

    public String getValuesString() {
        StringBuilder valueBuilder = new StringBuilder();
        values.forEach((value) -> valueBuilder.append(value));
        return valueBuilder.toString();
    }


    public void setReplaceValues(List<String> replaceValues) {
        this.replaceValues = replaceValues;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Diff diff = (Diff) o;
        return Objects.equals(index, diff.index) &&
                Objects.equals(operation, diff.operation) &&
                areEqualLists(replaceValues, diff.replaceValues) &&
                areEqualLists(values, diff.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operation, index, replaceValues, values);
    }

    private boolean areEqualLists(List<String> firstList, List<String> secondList) {
        if (firstList.size() != secondList.size()) {
            return false;
        }

        int idx = 0;
        for (Object o : firstList) {
            if (!o.equals(secondList.get(idx))) {
                return false;
            }
            idx++;
        }
        return true;
    }
}
