package at.jku.isse.ecco.genericAdapter.grammarInferencer.data;

import java.util.List;

/**
 * @author Michael Jahn
 */
public abstract class Symbol {


    protected String name;
    private final int id;
    private boolean isRecursionNonTerminal = false;

    Symbol(String name) {
        this.id = UniqueIdGenerator.getNextId();
        this.name = name;
    }

    Symbol() {
        this.id = UniqueIdGenerator.getNextId();
    }

    public String getName() {
        return name;
    }

    public abstract boolean isTerminal();

    public abstract boolean isNonTerminal();

    public int getId() {
        return id;
    }

    // returns the terminal beginnings of the symbol
    public abstract List<Terminal> getTerminalBeginningSymbols();

    // returns the terminal beginnings of the symbol
    public abstract List<String> getTerminalBeginningValues();

    public abstract Symbol getDeepCopyForSnapshot();

    public abstract boolean isEndingSymbol(List<String> sampleSeperator);

    /**
     * @return if this symbol is a "structure" node in the grammar, that should directly influence the resulting ecco model tree
     */
    public boolean isStructureSymbol() {
        return false;
    }

    public boolean isRecursionNonTerminal() {
        return isRecursionNonTerminal;
    }

    public abstract boolean isOptionalSymbol();

    public void setRecursionNonTerminal(boolean recursionNonTerminal) {
        isRecursionNonTerminal = recursionNonTerminal;
    }
}
