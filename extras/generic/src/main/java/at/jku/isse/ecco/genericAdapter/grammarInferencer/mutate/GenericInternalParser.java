package at.jku.isse.ecco.genericAdapter.grammarInferencer.mutate;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Symbol;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.ParameterSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * Generic Parser that can parse a grammar instantly with the given {@link NonTerminal} rootSymbol
 *
 * @author Michael Jahn
 */
public class GenericInternalParser {

    /**
     * @param rootSymbol
     * @param tokenList
     * @return true if the tokenized sample {@link List<String>} can be parse by the given grammar {@link NonTerminal}, false otherwise
     */
    public boolean parsesTokenizedSample(NonTerminal rootSymbol, List<String> tokenList) {
        return !findParseError(new Stack<>(), tokenList, rootSymbol, false);
    }

    /**
     * Parses a tokenized sample with the given grammar ({@link NonTerminal}) and returns the resulting parsed rules
     *
     * @param rootSymbol
     * @param tokenizedSample
     * @return parsed rules or null, if the sample could not be parsed
     */
    public List<Rule> parseTokenizedSample(NonTerminal rootSymbol, List<String> tokenizedSample) {
        List<RuleParsingPosition> ruleParsingPositionList = new ArrayList<>();
        Stack<RuleParsingPosition> localStack = new Stack<>();

        if(parseNonTerminal(rootSymbol, new ArrayList<>(tokenizedSample), false, localStack, ruleParsingPositionList, false)) {
            System.out.print("Error found, rule stack was: ");
            while (!localStack.empty()) {
                RuleParsingPosition curStackPosition = localStack.pop();
                System.out.print(" ||" + curStackPosition.getRule() + " before: " + curStackPosition.getCurPosition() + "(" + curStackPosition.getRule().getSymbols().get(curStackPosition.getCurPosition()) + ") ||");
            }
            System.out.println();
            return null;
        }

        List<Rule> parsedRules = new ArrayList<>();
        for (RuleParsingPosition ruleParsingPosition : ruleParsingPositionList) {
            assert ruleParsingPosition.getCurPosition() >= ruleParsingPosition.getRule().getSymbols().size();

            parsedRules.add(ruleParsingPosition.getRule());
        }

        return parsedRules;
    }

    /**
     * Tries to inferGrammar the given token list with the given grammar {@link NonTerminal}
     * fills the field {@link Stack < RuleParsingPosition >} that contains an empty stack, or the erronous position afterwards
     *
     * @param ruleStackPositions
     * @param tokenList          .
     * @param rootSymbol         .
     * @param stopOnFirstError if true, the parser will stop at the first error that occurs,
     *                         otherwise it will try all rules and stop at the last one if an error is found
     * @return true, if a inferGrammar error was found, false otherwise
     */
    public boolean findParseError(Stack<RuleParsingPosition> ruleStackPositions, List<String> tokenList, NonTerminal rootSymbol, boolean stopOnFirstError) {
        List<String> localTokenList = new ArrayList<>(tokenList);

//        ruleStackPositions = new Stack<>();

        boolean parseError = parseNonTerminal(rootSymbol, localTokenList, !stopOnFirstError, ruleStackPositions, new ArrayList<>(), stopOnFirstError);

        Stack<RuleParsingPosition> localStack = new Stack<>();
        localStack.addAll(ruleStackPositions);

        if (parseError) {
            if (ParameterSettings.DEBUG_OUTPUT) {
                System.out.print("Error found, rule stack was: ");
                while (!localStack.empty()) {
                    RuleParsingPosition curStackPosition = localStack.pop();
                    System.out.print(" ||" + curStackPosition.getRule() + " before: " + curStackPosition.getCurPosition() + "(" + curStackPosition.getRule().getSymbols().get(curStackPosition.getCurPosition()) + ") ||");
                }
                System.out.println();
            }
            return true;
        } else if (localTokenList.size() > 0) {
            if(ParameterSettings.DEBUG_OUTPUT) {
                System.out.print("Error found, tokens were not parsed: ");
                localTokenList.forEach(System.out::print);
                System.out.println();
            }
            return true;
        } else {
            if (ParameterSettings.DEBUG_OUTPUT) System.out.println("STACK PARSING FINISHED WITHOUT ERROR!");
        }
        return false;
    }

    boolean parseNonTerminal(NonTerminal curSymbol, List<String> tokenList, boolean deleteTokensInErrorCase, Stack<RuleParsingPosition> ruleStackPositions, List<RuleParsingPosition> ruleList, boolean stopOnFirstError) {

        int idx = 0;

        for (Rule rule : curSymbol.getRules()) {
            int ruleStackSizeOld = ruleStackPositions.size();
            int oldTokenListSize = tokenList.size();
            int ruleListSizeOld = ruleList.size();
            boolean parseRuleResult = parseRule(ruleStackPositions, tokenList, rule, ruleList, deleteTokensInErrorCase, stopOnFirstError);
            if (!parseRuleResult) {
                return false;
            } else if (stopOnFirstError && parseRuleResult && anyNonEmptySymbolsWhereParsed(ruleStackPositions, ruleStackSizeOld, ruleList, ruleListSizeOld)/*(ruleStackPositions.get(ruleStackSizeOld).getCurPosition() > 0 || ruleStackPositions.peek().getCurPosition() > 0)*/ && !rulesListContainsOnlyEmptySymbols(ruleList)) {
                return true;
            } else if (idx < curSymbol.getRules().size() - 1) {
                while (ruleStackPositions.size() > ruleStackSizeOld) {
                    ruleStackPositions.pop();
                }

                while (ruleList.size() > ruleListSizeOld) {
                    ruleList.remove(ruleList.get(ruleList.size() - 1));
                }

//                ruleStackPositions.pop();
//                ruleList.remove(ruleList.get(ruleList.size() - 1));
            }
            idx++;
        }
        return true;
    }

    private boolean parseRule(Stack<RuleParsingPosition> ruleStackPositions, List<String> tokenList, Rule rule, List<RuleParsingPosition> ruleList, boolean deleteTokensInErrorCase, boolean stopOnFirstError) {
        RuleParsingPosition curRuleStackPosition = new RuleParsingPosition(rule, 0);
        RuleParsingPosition curRuleListPosition = new RuleParsingPosition(rule, 0);
        ruleStackPositions.push(curRuleStackPosition);
        ruleList.add(curRuleListPosition);


        List<String> localTokenList = new ArrayList<>();
        localTokenList.addAll(tokenList);
//        List<String> deletedTokensList = new ArrayList<>();

        int curSymbolIdx = 0;
        for (Symbol curSymbol : rule.getSymbols()) {
            if (curSymbol.isTerminal()) {
                if (localTokenList.size() <= 0 || !localTokenList.get(0).equals(curSymbol.getName())) {
                    curRuleStackPosition.setCurPosition(curSymbolIdx);
                    curRuleListPosition.setCurPosition(curSymbolIdx);
                    // restore deleted tokens
//                    for (int i = deletedTokensList.size() - 1; i >= 0; i--) {
//                        localTokenList.add(0, deletedTokensList.get(i));
//                    }
                    if (deleteTokensInErrorCase) {
                        int parsedTokens = tokenList.size() - localTokenList.size();
                        for (int i = 0; i < parsedTokens; i++) {
                            tokenList.remove(0);
                        }
                    }
                    return true;
                } else {
                    curRuleStackPosition.setCurPosition(curSymbolIdx);
                    curRuleListPosition.setCurPosition(curSymbolIdx);

                    // check needed to support empty symbol
                    if (!curSymbol.getName().equals("")) {
//                        deletedTokensList.add(localTokenList.get(0));
                        curRuleListPosition.incCurPosition(1);
                        localTokenList.remove(0);
                    }
                }
            } else if (parseNonTerminal((NonTerminal) curSymbol, localTokenList, false, ruleStackPositions, ruleList, stopOnFirstError)) {
                curRuleStackPosition.setCurPosition(curSymbolIdx);
                curRuleListPosition.setCurPosition(curSymbolIdx);
                // restore deleted tokens
//                for (int i = deletedTokensList.size() - 1; i >= 0; i--) {
//                    tokenList.add(0, deletedTokensList.get(i));
//                }
                if (deleteTokensInErrorCase) {
                    int parsedTokens = tokenList.size() - localTokenList.size();
                    for (int i = 0; i < parsedTokens; i++) {
                        tokenList.remove(0);
                    }
                }
                return true;
            }
            curSymbolIdx++;
        }
        // delete parsed tokens from token list
        int parsedTokens = tokenList.size() - localTokenList.size();
        for (int i = 0; i < parsedTokens; i++) {
            tokenList.remove(0);
        }

        curRuleListPosition.setCurPosition(curSymbolIdx);
        ruleStackPositions.pop();
        return false;
    }



    private boolean anyNonEmptySymbolsWhereParsed(Stack<RuleParsingPosition> stack, int oldRuleStackSize, List<RuleParsingPosition> ruleList, int oldRuleListSize) {
        List<Symbol> parsedSymbols = new ArrayList<>();
        for (RuleParsingPosition r : ruleList.subList(oldRuleListSize, ruleList.size())) {
            if(r.getRule().getSymbols().subList(0, r.getCurPosition()).size() > 0) {
                parsedSymbols.addAll(r.getRule().getSymbols().subList(0, r.getCurPosition()));
            }
        }
        return parsedSymbols.stream().filter(s -> !s.getName().equals("") && s.isTerminal()).collect(toList()).size() > 0;
    }

    private boolean rulesListContainsOnlyEmptySymbols(List<RuleParsingPosition> rulesList) {

        List<String> parsedTokens = new ArrayList<>();
        rulesList.forEach(ruleParsingPosition -> parsedTokens.addAll(ruleParsingPosition.getRule().getSymbols().subList(0, ruleParsingPosition.getCurPosition()).stream()
                .filter(s -> s.isTerminal()).map(s -> s.getName()).collect(Collectors.<String>toList())));

        List<String> parsedTokensNonEmpty = parsedTokens.stream().filter(token -> !token.isEmpty()).collect(toList());

        return parsedTokensNonEmpty.size() == 0;
    }

}
