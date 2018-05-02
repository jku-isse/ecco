package at.jku.isse.ecco.genericAdapter.grammarInferencer.data;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * Represents a grammar rule, as a list of symbols
 *
 * @author Michael Jahn
 */
public class Rule {

    private static final String NON_TERMINAL_TRANSFORMED = "TRANSFORMEND_NON_TERMINAL:";

    private NonTerminal parentNonTerminal;

    private final List<Symbol> symbols;

    public Rule(List<Symbol> symbols) {
        this.symbols = symbols;
    }

    public List<Symbol> getSymbols() {
        return symbols;
    }

    public void insertSymbols(int index, List<Symbol> insertSymbols) {
        int i = index;
        for (Symbol curSymbol : insertSymbols) {
            symbols.add(i, curSymbol);
            i++;
        }
    }

    public void deleteSymbols(int startIndex, int nrElements) {
        for (int i = startIndex; i < startIndex + nrElements; i++) {
            if(symbols.size() > startIndex) symbols.remove(startIndex);
        }
    }

    public void replaceSymbols(int startIndex, int nrReplaceElements, List<Symbol> newSymbols) {
        deleteSymbols(startIndex, nrReplaceElements);
        insertSymbols(startIndex, newSymbols);
    }

    public void replaceSymbol(int index, Symbol newSymbol) {
        deleteSymbols(index, 1);
        insertSymbols(index, Collections.singletonList(newSymbol));
    }


    public List<Terminal> getFirstTerminals() {
        List<Terminal> terminalList = new ArrayList<>();
        Symbol firstSymbol = symbols.get(0);
        if (firstSymbol.isTerminal()) {
            terminalList.add((Terminal) firstSymbol);
        } else {
            NonTerminal nonTerminalSymbol = (NonTerminal) firstSymbol;
            for (Rule rule : nonTerminalSymbol.getRules()) {
                terminalList.addAll(rule.getFirstTerminals());
            }
        }
        return terminalList;
    }

    public boolean startsWithTerminal() {
        return symbols.size() >= 1 && symbols.get(0).isTerminal();
    }

    public boolean startsWithTerminal(Terminal terminal) {
        return symbols.size() >= 1 && symbols.get(0).isTerminal() && symbols.get(0).equals(terminal);
    }


    @Override
    public String toString() {
        StringBuilder strBuilder = new StringBuilder();

        for (Symbol symbol : symbols) {
          if (symbol.isRecursionNonTerminal()) {
                strBuilder.append(((NonTerminal) symbol).getRules().get(0).getSymbols().get(0).getName().toLowerCase() + "* ");
            } else {
                strBuilder.append(symbol.getName().toLowerCase() + " ");
            }
        }
        return strBuilder.toString();
    }

    public String toAntlrString() {
        StringBuilder strBuilder = new StringBuilder();

        for (Symbol symbol : symbols) {
            if (symbol.isTerminal()) {
                strBuilder.append("'" + symbol.getName() + "'" + " ");
            } else if (symbol.isRecursionNonTerminal()) {
                strBuilder.append(((NonTerminal) symbol).getRules().get(0).getSymbols().get(0).getName().toLowerCase() + "* ");
            } else if (symbol.isNonTerminal() && ((NonTerminal) symbol).isImplicitRecursionNonTerminal()) {
                strBuilder.append(symbol.getName().toLowerCase() + "* ");
            } else if(symbol.isOptionalSymbol()) {
                strBuilder.append(symbol.getName().toLowerCase() + "? ");
            } else {
                strBuilder.append(symbol.getName().toLowerCase() + " ");
            }
        }
        return strBuilder.toString();
    }



    public void appendSymbol(Symbol curSymbol) {

        symbols.add(curSymbol);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Rule rule = (Rule) o;


        if (symbols != null ? !symbols.equals(rule.symbols) : rule.symbols != null) return false;


        if (symbols != null) {
            List<Symbol> otherSymbols = ((Rule) o).getSymbols();
            if (symbols.size() != otherSymbols.size()) {
                return false;
            }
            for (int i = 0; i < symbols.size(); i++) {
                if (!symbols.get(i).getName().equals(otherSymbols.get(i).getName())) {
                    return false;
                }
            }
            return true;
        }

        return true;
    }

    @Override
    public int hashCode() {

        if (symbols != null) {
            StringBuilder strB = new StringBuilder();
            final int prime = 31;
            int result = 1;
            for (Symbol s : symbols) {
                strB.append(s.getName());
            }
            result = strB.toString().hashCode();
            return result;
        }

        return 0;
    }


    public NonTerminal getParentNonTerminal() {
        return parentNonTerminal;
    }

    public void setParentNonTerminal(NonTerminal parentNonTerminal) {
        this.parentNonTerminal = parentNonTerminal;
    }

    public void replaceNonTerminalWithSymbolsRecursive(NonTerminal replaceNonTerminal, List<Symbol> newSymbols) {
        for (int i = 0; i < symbols.size(); i++) {
            if (symbols.get(i).equals(replaceNonTerminal)) {
                this.replaceSymbols(i, 1, newSymbols);
            } else if (symbols.get(i).isNonTerminal()) {
                ((NonTerminal) symbols.get(i)).replaceNonTerminalWithSymbolsRecursive(replaceNonTerminal, newSymbols);
            }
        }
    }

    public Rule getDeepCopyForSnapshot() {
        List<Symbol> copiedSymbols = symbols.stream().map(Symbol::getDeepCopyForSnapshot).collect(toList());
        return new Rule(copiedSymbols);
    }

    public List<List<String>> getFollowers(int idx) {

        List<List<String>> possibleFollowingSymbols = new ArrayList<>();
        possibleFollowingSymbols.add(new ArrayList<>());
        for (Symbol symbol : getSymbols().subList(idx, getSymbols().size())) {
            if (symbol.isTerminal()) {
                for (List<String> possibleFollowingSymbol : possibleFollowingSymbols) {
                    possibleFollowingSymbol.add(symbol.getName());
                }
            } else {
                List<List<String>> followerLists = ((NonTerminal) symbol).getFollowers();
                List<List<String>> tmpLists = new ArrayList<>();
                for (List<String> singleFollowerList : possibleFollowingSymbols) {
                    for (List<String> followerList : followerLists) {
                        if (followerLists.indexOf(followerList) > 0) {
                            List<String> newList = new ArrayList<>(singleFollowerList);
                            newList.addAll(followerList);
                            tmpLists.add(newList);
                        }
                    }
                    singleFollowerList.addAll(followerLists.get(0));
                }
                possibleFollowingSymbols.addAll(tmpLists);
            }
        }
        return possibleFollowingSymbols;
    }

    /**
     * Returns true if the rule ends with one of the given sampleSeperators
     *
     * @param sampleSeperator
     * @return
     */
    public boolean isEndingRule(List<String> sampleSeperator) {
        if(symbols.size() > 0) {
            Symbol lastSymbol = symbols.get(symbols.size() - 1);

            return lastSymbol.isEndingSymbol(sampleSeperator);
        }
        return false;
    }

    /**
     * @return if the rule contains at least one non-terminal symbol
     */
    public boolean containsNonTerminals() {
        return symbols.stream().filter(symbol -> symbol.isNonTerminal()).count() > 0;
    }

    public boolean isRecursiveRule() {
        return symbols.stream().filter(symbol -> symbol.isNonTerminal() && symbol.equals(parentNonTerminal)).count() > 0;
    }

    /**
     * @return true if there is a infinite nonTerminal among the symbols of this rule (check is non-recursive)
     */
    public boolean containsInfiniteRule() {
        for (Symbol symbol : symbols) {
            if(symbol.isNonTerminal() && ((NonTerminal) symbol).containsRecurisveRule()) return true;
        }
        return false;
    }

    /**
     * @return a {@link List<Terminal>} containing the recursive pattern included in this rule
     * or null if it is not a revursive rule
     */
    public List<Terminal> getRecursivePattern() {
        if(!isRecursiveRule()) return null;

        List<Terminal> terminals = new ArrayList<>();

        Symbol curSymbol = symbols.get(0);

        int i = 0;
        while(curSymbol.isTerminal()) {
            terminals.add((Terminal) curSymbol);
            i++;
            curSymbol = symbols.get(i);
        }
        return terminals;
    }

    public void transformNonTerminalForSerialization() {
        for (int i = 0; i < symbols.size(); i++) {
            if(symbols.get(i).isNonTerminal()) {
                replaceSymbol(i, new Terminal(NON_TERMINAL_TRANSFORMED + symbols.get(i).getName(), NON_TERMINAL_TRANSFORMED + symbols.get(i).getName()));
            }
        }
    }

    public void reTransformNonTerminalForSerialization(Map<String, NonTerminal> nonTerminals) {
        for (int i = 0; i < symbols.size(); i++) {
            String symbolName = symbols.get(i).getName();
            if(symbolName.startsWith(NON_TERMINAL_TRANSFORMED)) {
                NonTerminal replaceNonTerminal = nonTerminals.get(symbolName.substring(symbolName.lastIndexOf(':')+1));
                if(replaceNonTerminal == null) {
                    System.err.println("ERROR in serialization! Could not find nonTerminal with id: " + symbolName.substring(symbolName.lastIndexOf(':')));
                } else {
                    replaceSymbol(i, replaceNonTerminal);
                }
            }
        }
    }
}



