package at.jku.isse.ecco.genericAdapter.grammarInferencer.data;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.ParameterSettings;

import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * Represents a nonTerminal symbol
 *
 * @author Michael Jahn
 */
public class NonTerminal extends Symbol {

    public static final String NON_TERMINAL_NAME_PREFIX = "R";
    public static final String STRUCTURE_SYMBOL_NAME_PREFIX = "S";
    public static final String BLOCK_CONTENT_STRUCTURE_SYMBOL_NAME_PREFIX = "SB";

//    public static final Map<String, NonTerminal> existingNonTerminalMap = new HashMap<>();

    private List<Rule> rules;

    NonTerminal(String name) {
        super(name);
        rules = new ArrayList<>();
    }

    NonTerminal() {
        super();
        this.name = (isStructureSymbol() ? STRUCTURE_SYMBOL_NAME_PREFIX : NON_TERMINAL_NAME_PREFIX) + this.getId();
        rules = new ArrayList<>();
    }

    public List<Rule> getRules() {
        return new ArrayList<>(rules);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Rule rule : rules) {
            stringBuilder.append(name + ": " + rule.toString());
        }
        return stringBuilder.toString();
    }

    @Override
    public boolean isTerminal() {
        return false;
    }

    @Override
    public boolean isNonTerminal() {
        return true;
    }

    public void addRules(List<Rule> rules) {
        for (Rule rule : rules) {
            addRule(rule);
        }
    }

    public void addRule(Rule rule) {

        if (!rules.contains(rule)) {
            Rule newRule = null;
            Rule replaceRule = null;
            for (Rule rule1 : rules) {
                if (rule.getSymbols().size() > 0
                        && rule1.getSymbols().size() > 0
                        && rule.getSymbols().get(0).equals(rule1.getSymbols().get(0))) {

                    // resolve ambiguous rules
                    int commonPrefix = 0;
                    while (rule1.getSymbols().size() > commonPrefix && rule.getSymbols().size() > commonPrefix
                            && rule1.getSymbols().get(commonPrefix).equals(rule.getSymbols().get(commonPrefix))) {
                        commonPrefix++;
                    }

                    newRule = new Rule(rule1.getSymbols().stream().limit(commonPrefix).collect(toList()));

                    Rule oldRule = new Rule(rule1.getSymbols().subList(commonPrefix, rule1.getSymbols().size()));
                    Rule newSymbolsRule = new Rule(rule.getSymbols().subList(commonPrefix, rule.getSymbols().size()));
                    NonTerminal newNonterminal = NonTerminalFactory.createNewNonTerminal(newSymbolsRule);
                    newNonterminal.addRule(oldRule);

                    newRule.appendSymbol(newNonterminal);

                    replaceRule = rule1;
                }
            }

            if (newRule != null) {
                rules.remove(rules.indexOf(replaceRule));
                rules.add(newRule);
                newRule.setParentNonTerminal(this);
            } else {
                rules.add(rule);
                rule.setParentNonTerminal(this);
            }
            rules.sort((o1, o2) -> o2.getSymbols().size() - o1.getSymbols().size());

        }
    }

    /**
     * @return a list containing all nonTerminal Symbols included in this, recursive and no duplicates
     */
    public List<NonTerminal> getAllNonTerminals() {
        Set<NonTerminal> nonTerminals = new LinkedHashSet<>();

        nonTerminals.add(this);

        for (Rule rule : rules) {
            for (Symbol symbol : rule.getSymbols()) {
                if (symbol.isNonTerminal() && !nonTerminals.contains(symbol)) {
                    nonTerminals.addAll(((NonTerminal) symbol).getAllNonTerminals());
                }
            }
        }
        return new ArrayList<>(nonTerminals);
    }

    public Map<String, NonTerminal> getAllNonTerminalsMap(){
        Map<String, NonTerminal> nonTerminalMap = new HashMap<>();
        for (NonTerminal nonTerminal : getAllNonTerminalsInternal(new HashSet<>())) {
            nonTerminalMap.put(nonTerminal.getName(), nonTerminal);
        }
        return nonTerminalMap;
    }

    private Set<NonTerminal> getAllNonTerminalsInternal(Set<NonTerminal> processed) {
        processed.add(this);

        for (Rule rule : rules) {
            for (Symbol symbol : rule.getSymbols()) {
                if (symbol.isNonTerminal() && !processed.contains(symbol)) {
                    processed.addAll(((NonTerminal) symbol).getAllNonTerminalsInternal(processed));
                }
            }
        }
        return processed;
    }

    public Set<NonTerminal> getAllNonTerminalsRecursive() {
        return getAllNonTerminalsInternal(new LinkedHashSet<>());
    }

    public String subTreeToString() {
        StringBuilder strBuilder = new StringBuilder();
        Set<NonTerminal> nonTerminals = getAllNonTerminalsInternal(new LinkedHashSet<>());
        strBuilder.append(this.toString() + "\n");
        nonTerminals.remove(this);
        for (NonTerminal nonTerminal : nonTerminals) {
            if(!nonTerminal.isRecursionNonTerminal()) {
                strBuilder.append(nonTerminal.toString() + "\n");
            }
        }
        return strBuilder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NonTerminal that = (NonTerminal) o;

        return !(getName() != null ? !getName().equals(that.getName()) : that.getName() != null);

    }

    @Override
    public int hashCode() {
        return getName() != null ? getName().hashCode() : 0;
    }

    @Override
    public List<Terminal> getTerminalBeginningSymbols() {
        List<Terminal> terminalBeginnings = new ArrayList<>();
        rules.stream().filter(rule -> rule.getSymbols().size() >= 1).forEach(rule -> {
            terminalBeginnings.addAll(rule.getSymbols().get(0).getTerminalBeginningSymbols());
        });
        return terminalBeginnings;
    }

    @Override
    public List<String> getTerminalBeginningValues() {
        List<String> terminalBeginningValues = getTerminalBeginningSymbols().stream().map(Terminal::getValue).collect(toList());

        return terminalBeginningValues;
    }

    @Override
    public Symbol getDeepCopyForSnapshot() {
        NonTerminal copiedNonTerminal = NonTerminalFactory.getSnapshotNonTerminal(this.getName());
        if(copiedNonTerminal.getRules().isEmpty()) {
            for (Rule rule : rules) {
                // this special handling became necessary to support recursive rules
                if(rule.isRecursiveRule()) {
                    List<Symbol> copiedSymbols = new ArrayList<>();
                    for (Symbol symbol : rule.getSymbols()) {
                        if(symbol.isNonTerminal() && symbol.equals(copiedNonTerminal)) {
                            copiedSymbols.add(copiedNonTerminal);
                        } else {
                            copiedSymbols.add(symbol.getDeepCopyForSnapshot());
                        }
                    }
                    copiedNonTerminal.addRule(new Rule(copiedSymbols));
                } else {
                    copiedNonTerminal.addRule(rule.getDeepCopyForSnapshot());
                }
            }
        }
        return copiedNonTerminal;
    }

    @Override
    public boolean isEndingSymbol(List<String> sampleSeperator) {
        boolean allRulesEndingRules = true;

        for (Rule rule : rules) {
            if(rule.isEndingRule(sampleSeperator)) {
                if(!allRulesEndingRules) {
                    if(ParameterSettings.INFO_OUTPUT)
                        System.err.println("ATTENTION!, Not all rules of nonTerminal " + name + " are ending rules!");
                }
                allRulesEndingRules = true;

            } else {
                allRulesEndingRules = false;
            }
        }

        return allRulesEndingRules;
    }

    public void replaceNonTerminalWithSymbolsRecursive(NonTerminal replaceNonTerminal, List<Symbol> newSymbols) {
        for (Rule rule : rules) {
            rule.replaceNonTerminalWithSymbolsRecursive(replaceNonTerminal, newSymbols);
        }
    }

    /**
     * Returns a list of terminal symbol values contained in this nonTerminal
     * ATTENTION: only processes symbols until a nonTerminal with more than two rules is found
     *
     * @return List<String> or null
     */
    public List<String> getTerminalSymbolsValues() {

        if (rules.size() > 1) {
            return null;
        }
        List<String> terminalSymbolValues = new ArrayList<>();
        for (Symbol s : rules.get(0).getSymbols()) {
            if (s.isTerminal()) {
                terminalSymbolValues.add(s.getName());
            } else {
                List<String> nextTerminals = (((NonTerminal) s).getTerminalSymbolsValues());
                if (nextTerminals == null) {
                    return terminalSymbolValues;
                }
                terminalSymbolValues.addAll(nextTerminals);
            }
        }
        return terminalSymbolValues;
    }

    /**
     * @return all possible symbol combinations in this nonTerminal
     */
    public List<List<String>> getFollowers() {
        List<List<String>> possibleFollowerListAllRules = new ArrayList<>();

        for (Rule rule : rules) {
            List<List<String>> possibleFollowerList = new ArrayList<>();
            possibleFollowerList.add(new ArrayList<>());
            for (Symbol symbol : rule.getSymbols()) {
                if(symbol.isTerminal()) {
                    for (List<String> followerList : possibleFollowerList) {
                        followerList.add(symbol.getName());
                    }
                } else {
                    List<List<String>> followerLists = ((NonTerminal)symbol).getFollowers();
                    List<List<String>> tmpLists = new ArrayList<>();
                    for(List<String> singleFollowerList : possibleFollowerList) {
                        for (List<String> followerList : followerLists) {
                            if(followerLists.indexOf(followerList) > 0) {
                                List<String> newList = new ArrayList<>(singleFollowerList);
                                newList.addAll(followerList);
                                tmpLists.add(newList);
                            }
                        }
                        singleFollowerList.addAll(followerLists.get(0));
                    }
                    possibleFollowerList.addAll(tmpLists);
                }
            }
            possibleFollowerListAllRules.addAll(possibleFollowerList);
        }

        return possibleFollowerListAllRules;
    }

    /**
     * @return true if there is a resursive rule among the rules of this nonTerminal
     */
    public boolean containsRecurisveRule() {
        for (Rule rule : rules) {
            if(rule.isRecursiveRule()) return true;
        }
        return false;
    }

    @Override
    public boolean isStructureSymbol() {
        return false;
    }

    @Override
    public boolean isOptionalSymbol() {
        for (Rule rule : rules) {
            if(rule.getSymbols().size() == 0) {
                return true;
            }
        }
        return false;
    }


    public static boolean isStructureSymbolName(String name) {
        return name.toLowerCase().startsWith(STRUCTURE_SYMBOL_NAME_PREFIX.toLowerCase());
    }

    public boolean isImplicitRecursionNonTerminal() {
        if(!isRecursionNonTerminal()) {
            if(getRules().size() == 2) {
                // one empty rule and second rule ends with the same symbol nonTerminal
                if(getRules().get(0).getSymbols().size() == 0
                        && getRules().get(1).getSymbols().get(getRules().get(1).getSymbols().size() - 1).equals(this)) {
                    return true;
                } else if(getRules().get(1).getSymbols().size() == 0
                        && getRules().get(0).getSymbols().get(getRules().get(0).getSymbols().size() - 1).equals(this)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getImplicitRecursionAntlrRule() {
        if(!isRecursionNonTerminal()) {
            if(getRules().size() == 2) {
                // one empty rule and second rule ends with the same symbol nonTerminal
                if(getRules().get(0).getSymbols().size() == 0
                        && getRules().get(1).getSymbols().get(getRules().get(1).getSymbols().size() - 1).equals(this)) {
                    return new Rule(rules.get(1).getSymbols().subList(0, rules.get(1).getSymbols().size() - 1)).toAntlrString();
                } else if(getRules().get(1).getSymbols().size() == 0
                        && getRules().get(0).getSymbols().get(getRules().get(0).getSymbols().size() - 1).equals(this)) {
                    return new Rule(rules.get(0).getSymbols().subList(0, rules.get(0).getSymbols().size() - 1)).toAntlrString();
                }
            }
        }
        return "";
    }
}
