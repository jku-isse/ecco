package at.jku.isse.ecco.genericAdapter.grammarInferencer.mutate;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule;

/**
 * @author Michael Jahn
 */
public class RuleParsingPosition {

    private final Rule rule;
    private int curPosition;

    public RuleParsingPosition(Rule rule, int curPosition) {
        this.rule = rule;
        this.curPosition = curPosition;
    }

    public int getCurPosition() {
        return curPosition;
    }

    public void setCurPosition(int curPosition) {
        this.curPosition = curPosition;
    }

    public Rule getRule() {
        return rule;
    }

    public void incCurPosition(int incAmount) {
        curPosition += incAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RuleParsingPosition that = (RuleParsingPosition) o;

        if (curPosition != that.curPosition) return false;
        if (!rule.equals(that.rule)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = rule.hashCode();
        result = 31 * result + curPosition;
        return result;
    }
}
