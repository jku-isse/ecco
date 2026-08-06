package at.jku.isse.ecco.genericAdapter.grammarInferencer.mutate;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.*;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.diff.Diff;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.diff.DiffUtils;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.ParameterSettings;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.facade.Statistics;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * Module for mutating the grammar
 *
 * @author Michael Jahn
 */
public class GrammarMutator {

    private DiffOptimization diffOptimization = new DiffOptimization();
    public GenericInternalParser genericInternalParser = new GenericInternalParser();

    public NonTerminal mutateGrammar(List<Diff> diffList, NonTerminal rootSymbol, List<String> tokenList,
                                     List<String> sampleSeperator) {

        if (!genericInternalParser.parsesTokenizedSample(rootSymbol, tokenList)) {
            return mutateGrammar(diffList, rootSymbol, tokenList, true, sampleSeperator);
        }
        return rootSymbol;

    }

    private NonTerminal mutateGrammar(List<Diff> diffList, NonTerminal rootSymbol, List<String> tokenList,
                                      boolean useFallback, List<String> sampleSeperator) {

        NonTerminalFactory.saveGrammarSnapshot(rootSymbol);

        List<Diff> initialDiffList = DiffUtils.getDeepCopy(diffList);
        List<String> initialTokenList = new ArrayList<>(tokenList);

        diffList = diffOptimization.optimizeDiffList(diffList);

        List<Diff> localDiffList = new ArrayList<>(diffList);
        Stack<RuleParsingPosition> ruleStackPositions = new Stack<>();

        boolean parseError = genericInternalParser.findParseError(ruleStackPositions, tokenList, rootSymbol, true);
        try {
            // mutate grammar as long as a inferGrammar error is found
            while (localDiffList.size() > 0 && parseError) {

                // get next diff action
                if (localDiffList.size() >= 1) {
                    Diff curDiff = localDiffList.remove(0);
                    while (localDiffList.size() > 0 && curDiff.getOperation().equals(Diff.Operation.EQUAL)) {
                        curDiff = localDiffList.remove(0);
                    }

                    switch (curDiff.getOperation()) {
                        case REPLACE:
                            performReplaceMutation(diffList, curDiff, rootSymbol, ruleStackPositions, tokenList, true);
                            if (ParameterSettings.DEBUG_OUTPUT)
                                if (ParameterSettings.DEBUG_OUTPUT)
                                    System.out.println("> Performed replace mutation: " + ruleStackPositions.peek().getRule());
                            break;
                        case DELETE:
                            performDeleteMutation(diffList, curDiff, rootSymbol, ruleStackPositions, tokenList);
                            if (ParameterSettings.DEBUG_OUTPUT)
                                System.out.println("> Performed delete mutation: " + ruleStackPositions.peek().getRule());
                            break;
                        case INSERT:
                            localDiffList.add(0, curDiff);
                            performInsertMutation(localDiffList, rootSymbol, ruleStackPositions, tokenList);
                            localDiffList.remove(0);
                            if (ParameterSettings.DEBUG_OUTPUT)
                                System.out.println("> Performed insert mutation: " + ruleStackPositions.peek().getRule());
                            break;
                    }
                }
                ruleStackPositions = new Stack<>();
                parseError = genericInternalParser.findParseError(ruleStackPositions, tokenList, rootSymbol, true);
            }
        } catch (StackOverflowError | EmptyStackException e) {
            if (ParameterSettings.DEBUG_OUTPUT) {
                System.out.println("Exception occured during mutation: " + e);
            }
            if (ParameterSettings.CATCH_EXCEPTIONS_DURING_MUTATION) {
                if (ParameterSettings.DEBUG_OUTPUT) System.out.println("Using fallback diff:");

                initialDiffList = diffOptimization.generateFallbackDiff(initialDiffList);

                rootSymbol = NonTerminalFactory.applyCurrentGrammarSnapshot();

                return mutateFallbackGrammar(initialDiffList, rootSymbol, tokenList, sampleSeperator) ? rootSymbol : null;

            }

        }

        // default fallback
        // if the mutation did not manage to succeed, merge all diffs except for the common prefix and run mutation again
        if (useFallback && ParameterSettings.USE_DEFAULT_FALLBACK && parseError) {
            if (ParameterSettings.DEBUG_OUTPUT) {
                System.out.println("Mutation did not succeed, so use default fallback option");
            }
            initialDiffList = diffOptimization.generateFallbackDiff(initialDiffList);

            rootSymbol = NonTerminalFactory.applyCurrentGrammarSnapshot();

            return mutateFallbackGrammar(initialDiffList, rootSymbol, initialTokenList, sampleSeperator) ? rootSymbol : null;
        }


        return !genericInternalParser.findParseError(ruleStackPositions, tokenList, rootSymbol, true) ? rootSymbol : null;
    }

    /**
     * Takes the fallback diff and mutates the grammar, so that the sample can be parsed
     *
     * @param initialDiffList
     * @param rootSymbol
     * @param tokenList
     * @param sampleSeperator
     * @return true if the sample can be passed after mutation, false otherwise
     */
    public boolean mutateFallbackGrammar(List<Diff> initialDiffList, NonTerminal rootSymbol, List<String> tokenList, List<String> sampleSeperator) {

        if (genericInternalParser.parsesTokenizedSample(rootSymbol, tokenList)) {
            return true;
        }

        NonTerminalFactory.saveGrammarSnapshot(rootSymbol);
        Statistics.nrGeneralFallbackMutation++;

        // try to use an optimal fallback mutation
        if (!ParameterSettings.TRY_OPTIMAL_FALLBACK || !mutateFallbackGrammarOptimal(initialDiffList, rootSymbol, tokenList, sampleSeperator)) {
            rootSymbol = NonTerminalFactory.applyCurrentGrammarSnapshot();


            Stack<RuleParsingPosition> ruleStackPositions1 = new Stack<>();
            List<RuleParsingPosition> ruleListPositions = new ArrayList<>();
            genericInternalParser. parseNonTerminal(rootSymbol, tokenList, true, ruleStackPositions1,ruleListPositions, true);

            Stack<RuleParsingPosition> ruleStackPositions = new Stack<>();

            // mutate fallback only in root symbol
            boolean parseError = genericInternalParser.findParseError(ruleStackPositions, tokenList, rootSymbol, true);
            Diff replaceDiff = initialDiffList.get(0);
            Diff equalDiff = initialDiffList.get(0);
            int idx = 0;
            while (idx < initialDiffList.size() && replaceDiff.getOperation().equals(Diff.Operation.EQUAL)) {
                replaceDiff = initialDiffList.get(idx);
                idx++;
            }

            if (parseError) {
                Rule modifyRule = ruleStackPositions.get(0).getRule();
                int modifyPosition = ruleStackPositions.get(0).getCurPosition();

                Rule newTokensRule = new Rule(replaceDiff.getValues().stream().map((s) -> new Terminal(s, s)).collect(toList()));
                if (modifyRule.getSymbols().size() - modifyPosition <= 1 && modifyRule.getSymbols().get(modifyPosition).isNonTerminal()) {
                    ((NonTerminal) modifyRule.getSymbols().get(modifyPosition)).addRule(newTokensRule);
                } else {
                    Rule replaceTokensRule = new Rule(modifyRule.getSymbols().subList(modifyPosition, modifyRule.getSymbols().size()).stream().collect(toList()));
                    NonTerminal newNonTerminal = NonTerminalFactory.createNewNonTerminal(replaceTokensRule);
                    newNonTerminal.addRule(newTokensRule);
                    modifyRule.replaceSymbols(modifyPosition, modifyRule.getSymbols().size() - modifyPosition, new ArrayList<>(Arrays.asList(newNonTerminal)));
                }
            }
        }

        boolean success = !genericInternalParser.findParseError(new Stack<>(), tokenList, rootSymbol, false);

        if (success) Statistics.successGeneralFallbackMutation++;
        return success;

    }


    /**
     * Try to mutate the grammar with the fallback diff, but as optimal as possible
     *
     * @param initialDiffList
     * @param rootSymbol
     * @param tokenList
     * @return true if the sample can be parsed after mutation, false otherwise
     */
    public boolean mutateFallbackGrammarOptimal(List<Diff> initialDiffList, NonTerminal rootSymbol,
                                                List<String> tokenList, List<String> sampleSeperator) {
        Stack<RuleParsingPosition> ruleStackPositions = new Stack<>();
        List<RuleParsingPosition> parsedRulesList = new ArrayList<>();

        boolean parseError = genericInternalParser.parseNonTerminal(rootSymbol, tokenList, false, ruleStackPositions, parsedRulesList, true);

        Diff replaceDiff = initialDiffList.get(0);
        Diff equalDiff = initialDiffList.get(0);
        int idx = 0;
        while (idx < initialDiffList.size() && replaceDiff.getOperation().equals(Diff.Operation.EQUAL)) {
            replaceDiff = initialDiffList.get(idx);
            idx++;
        }

        // check if the replaceDiff string contains repeating patterns
        List<GrammarMutatorUtils.RepeatingPattern> filteredValuePatterns = new ArrayList<>();
        List<GrammarMutatorUtils.RepeatingPattern> filteredReplacePatterns = new ArrayList<>();

        List<GrammarMutatorUtils.RepeatingPattern> replacePatterns = GrammarMutatorUtils.computeShortestRepeatedPatterns(replaceDiff.getValues(), true);
        List<GrammarMutatorUtils.RepeatingPattern> valuePatterns = GrammarMutatorUtils.computeShortestRepeatedPatterns(replaceDiff.getReplaceValues(), true);
        if (valuePatterns.stream().filter(pattern -> pattern.getNrRepetitions() > ParameterSettings.MIN_NR_REPETITIONS).collect(toList()).size() > 0
                || replacePatterns.stream().filter(pattern -> pattern.getNrRepetitions() > ParameterSettings.MIN_NR_REPETITIONS).collect(toList()).size() > 0) {

            // TODO [7.10] mutate grammar to use a loop rule for the repeating pattern(s) in valuePatterns and replacePatterns
            filteredValuePatterns = valuePatterns.stream().filter(pattern -> pattern.getNrRepetitions() > ParameterSettings.MIN_NR_REPETITIONS).collect(toList());
            filteredReplacePatterns = replacePatterns.stream().filter(pattern -> pattern.getNrRepetitions() > ParameterSettings.MIN_NR_REPETITIONS).collect(toList());
//            filteredValuePatterns.stream().forEach(pattern -> pattern.setStartIdx(pattern.getStartIdx() + equalDiff.getValues().size()));
//            filteredReplacePatterns.stream().forEach(pattern -> pattern.setStartIdx(pattern.getStartIdx() + equalDiff.getValues().size()));
        }

        if (parseError) {
            Rule modifyRule;
            int modifyPosition;
            if (equalDiff.getOperation().equals(Diff.Operation.EQUAL) && equalDiff.getValues().size() > 0) {
                modifyRule = ruleStackPositions.peek().getRule();
                modifyPosition = ruleStackPositions.pop().getCurPosition();

                while (ruleStackPositions.size() > 0 && modifyPosition <= 0) {
                    modifyRule = ruleStackPositions.peek().getRule();
                    modifyPosition = ruleStackPositions.pop().getCurPosition();
                }

                // TODO need to ensure that the equals tokens are really equal with the modifyRule + position and the parsed tokens !!!
                if (true) {
                    Stack<RuleParsingPosition> equalDiffStackPosition = new Stack<>();
                    List<RuleParsingPosition> equalDiffListPosition = new ArrayList<>();
                    genericInternalParser.parseNonTerminal(rootSymbol, equalDiff.getValues(), false, equalDiffStackPosition, equalDiffListPosition, true);

                    // test / debug this method for now
//                    Stack<RuleParsingPosition> equalDiffStack2 = cleanRuleParsingStack2(equalDiffStackPosition, equalDiffListPosition, false);

                    Stack<RuleParsingPosition> zeroPreservedStack = cleanRuleParsingStack(equalDiffStackPosition, new ArrayList<>(equalDiffListPosition), true);
                    equalDiffStackPosition = cleanRuleParsingStack(equalDiffStackPosition, new ArrayList<>(equalDiffListPosition), false);

                    if (equalDiffStackPosition.size() > 0) {
                        modifyRule = equalDiffStackPosition.peek().getRule();
                        modifyPosition = equalDiffStackPosition.pop().getCurPosition();

                        //TODO [13.10] also use the repeting pattern in the fallback mutations
                        if (filteredValuePatterns.size() > 0) {

                            // nothing to do at the moment
                            // if the repeating pattern and the modifyPosition match, mutate grammar to allow infinite repetions of this pattern
                           /* for (GrammarMutatorUtils.RepeatingPattern pattern : filteredValuePatterns) {

                                replaceRuleWithInfiniteRepetitions(pattern, modifyRule, modifyPosition);

                                if (!genericInternalParser.findParseError(new Stack<>(), tokenList, rootSymbol, true)) {
                                    return true;
                                } else {
                                    // prepare new modifyPosition for possible further mutations
                                    equalDiffListPosition = new ArrayList<>();
                                    equalDiffStackPosition = new Stack<>();
                                    genericInternalParser.parseNonTerminal(rootSymbol, tokenList, false, equalDiffStackPosition, equalDiffListPosition, true);

                                    zeroPreservedStack = cleanRuleParsingStack(equalDiffStackPosition, new ArrayList<>(equalDiffListPosition), true);
                                    equalDiffStackPosition = cleanRuleParsingStack(equalDiffStackPosition, new ArrayList<>(equalDiffListPosition), false);

                                    modifyRule = equalDiffStackPosition.peek().getRule();
                                    modifyPosition = equalDiffStackPosition.pop().getCurPosition();
                                }
                            }*/


                        } else if (filteredReplacePatterns.size() > 0) {

                            /*for (GrammarMutatorUtils.RepeatingPattern pattern : filteredReplacePatterns) {

                                replaceRuleWithInfiniteRepetitions(pattern, modifyRule, modifyPosition);

                                if (!genericInternalParser.findParseError(new Stack<>(), tokenList, rootSymbol, true)) {
                                    return true;
                                } else {
                                    // prepare new modifyPosition for possible further mutations
                                    equalDiffListPosition = new ArrayList<>();
                                    equalDiffStackPosition = new Stack<>();
                                    genericInternalParser.parseNonTerminal(rootSymbol, tokenList, false, equalDiffStackPosition, equalDiffListPosition, true);

                                    zeroPreservedStack = cleanRuleParsingStack(equalDiffStackPosition, new ArrayList<>(equalDiffListPosition), true);
                                    equalDiffStackPosition = cleanRuleParsingStack(equalDiffStackPosition, new ArrayList<>(equalDiffListPosition), false);

                                    modifyRule = equalDiffStackPosition.peek().getRule();
                                    modifyPosition = equalDiffStackPosition.pop().getCurPosition();
                                }
                            }*/
                        }

                        // ensure that mutation takes place in an ending rule
                        Rule origModifyRule = modifyRule;
                        int origModifyPosition = modifyPosition;
                        if (!modifyRule.isEndingRule(sampleSeperator)) {
                            Statistics.nrEnhancedFallbackMutation++;
                            while (!modifyRule.isEndingRule(sampleSeperator) && !zeroPreservedStack.empty()) {
                                modifyRule = zeroPreservedStack.peek().getRule();
                                modifyPosition = zeroPreservedStack.pop().getCurPosition();
                            }

                            // perform rotating mutation, because modifyRule was originally no ending Rule and enhanced mutations need to be performed

                            // new tokens from replace diff
                            Rule replaceTokensRule = new Rule(replaceDiff.getValues().stream().map((s) -> new Terminal(s, s)).collect(toList()));

                            // all tokens that would come after the modifyRulePosition
                            Rule existingTokensRule = new Rule(getSymbolsFromGrammarAfterPosition(rootSymbol, origModifyRule, origModifyPosition, sampleSeperator));
                            NonTerminal newNonTerminal = NonTerminalFactory.createNewNonTerminal(replaceTokensRule);
                            newNonTerminal.addRule(existingTokensRule);

                            // new tokens from equal diff
                            int parsedTokens = inductParsedTokens(zeroPreservedStack).size();

                            Rule newEqualTokensRule = new Rule(equalDiff.getValues().subList(parsedTokens, equalDiff.getValues().size()).stream().map((s) -> new Terminal(s, s)).collect(toList()));
                            newEqualTokensRule.appendSymbol(newNonTerminal);
                            modifyRule.getParentNonTerminal().addRule(newEqualTokensRule);
                            boolean success = genericInternalParser.parsesTokenizedSample(rootSymbol, tokenList);
                            if (success) Statistics.successFullEnhancedFallbackMutations++;
                            return success;
                        }
                    }
                }

               /* // remove already parsed symbols from replace diff
                if(modifyRule.getSymbols().get(modifyPosition).isTerminal()) {
                    int origReplaceValuesCount = replaceDiff.getValues().size();
                    replaceDiff = removeParsedPrefixTokens2(initialDiffList, replaceDiff, tokenList, rootSymbol);
                }*/

            } else {
                modifyRule = ruleStackPositions.get(0).getRule();
                modifyPosition = ruleStackPositions.get(0).getCurPosition();
            }

            Rule newTokensRule = new Rule(replaceDiff.getValues().stream().map((s) -> new Terminal(s, s)).collect(toList()));

            // check if the following symbols are equal to the last replace tokens
            /*List<List<String>> possibleFollowingSymbols = new ArrayList<>();
            possibleFollowingSymbols.add(new ArrayList<>());
            for (ListIterator<RuleParsingPosition> iterator = ruleStackPositions.listIterator(0); iterator.hasNext(); ) {
                RuleParsingPosition ruleStackPosition = iterator.next();
                List<List<String>> curPossibleFollowingSymbols = ruleStackPosition.getRule().getFollowers(ruleStackPosition.getCurPosition() + 1);
                List<List<String>> tmpLists = new ArrayList<>();
                for (List<String> singleFollowerList : possibleFollowingSymbols) {
                    for (List<String> followerList : curPossibleFollowingSymbols) {
                        if (curPossibleFollowingSymbols.indexOf(followerList) > 0) {
                            List<String> newList = new ArrayList<>(singleFollowerList);
                            newList.addAll(followerList);
                            tmpLists.add(newList);
                        }
                    }
                    singleFollowerList.addAll(curPossibleFollowingSymbols.get(0));
                }
                possibleFollowingSymbols.addAll(tmpLists);
            }*/


            if (modifyRule.getSymbols().size() - modifyPosition <= 1 && modifyRule.getSymbols().get(modifyPosition).isNonTerminal()) {
                ((NonTerminal) modifyRule.getSymbols().get(modifyPosition)).addRule(newTokensRule);
            } else {

                Rule replaceTokensRule = new Rule(modifyRule.getSymbols().subList(modifyPosition, modifyRule.getSymbols().size()).stream().collect(toList()));
                NonTerminal newNonTerminal = NonTerminalFactory.createNewNonTerminal(replaceTokensRule);
                newNonTerminal.addRule(newTokensRule);
                modifyRule.replaceSymbols(modifyPosition, modifyRule.getSymbols().size() - modifyPosition, new ArrayList<>(Arrays.asList(newNonTerminal)));

                if(!genericInternalParser.parsesTokenizedSample(rootSymbol, tokenList)) {

                    // if the fallback mutation failed, try to remove tokens that are already parsed by other rules
                    Stack<RuleParsingPosition> localErrorStack = new Stack<>();
                    List<RuleParsingPosition> localRuleListPositions = new ArrayList<>();
                    genericInternalParser.parseNonTerminal(rootSymbol, tokenList, false, localErrorStack, localRuleListPositions, true);

                    // remove the newNonTerminal, as it does not count to the alreadyParsedTokens in this case
                    int ruleListIdx = localRuleListPositions.size() - 1;
                    while(ruleListIdx > 0 && !localRuleListPositions.get(ruleListIdx).getRule().getParentNonTerminal().equals(newNonTerminal)) {
                        ruleListIdx--;
                    }
                    if(ruleListIdx > 0) {
                        localRuleListPositions = localRuleListPositions.subList(0, ruleListIdx);
                    }

                    List<Symbol> inductedTokens = inductParsedTokens2(localRuleListPositions);

                    // if the inductedTokens are longer than the equal diff, this means that some tokens from the replaceDiff are already parsed by previous rules,
                    // and they can be removed from the newTokensRule
                    if(equalDiff.getOperation().equals(Diff.Operation.EQUAL) && inductedTokens.size() > equalDiff.getValues().size()) {
                        newTokensRule.deleteSymbols(0, inductedTokens.size() - equalDiff.getValues().size());
                    }
                }
            }
        }
        return genericInternalParser.parsesTokenizedSample(rootSymbol, tokenList);

    }

    /**
     * @param ruleListPositions
     * @return the list of parsed tokens that are found in the given {@link List<RuleParsingPosition>}
     */
    private List<Symbol> inductParsedTokens2(List<RuleParsingPosition> ruleListPositions) {
        List<String> parsedTokens = new ArrayList<>();

        return reconstructTerminalSymbols2(new ArrayList<>(ruleListPositions));
    }

    /**
     * Recursivley reconstruct the terminalSymbols that were parsed by the first {@link RuleParsingPosition} in the given ruleListPositions
     *
     * @param ruleListPositions
     * @return
     */
    List<Symbol> reconstructTerminalSymbols2(List<RuleParsingPosition> ruleListPositions) {

        if(ruleListPositions.size() <= 0) {
            return new ArrayList<>();
        }

        RuleParsingPosition ruleParsingPosition = ruleListPositions.get(0);
        ruleListPositions.remove(0);

        if (!ruleParsingPosition.getRule().containsNonTerminals()) {
            return new ArrayList<>(ruleParsingPosition.getRule().getSymbols().subList(0, ruleParsingPosition.getCurPosition()));
        } else {
            List<Symbol> terminalList = new ArrayList<>();

            for (Symbol symbol : ruleParsingPosition.getRule().getSymbols().subList(0, ruleParsingPosition.getCurPosition())) {
                if (symbol.isTerminal()) {
                    terminalList.add(symbol);
                } else {
                    terminalList.addAll(reconstructTerminalSymbols2(ruleListPositions));
                }
            }

            if(ruleParsingPosition.getCurPosition() < ruleParsingPosition.getRule().getSymbols().size()
                    && ruleParsingPosition.getRule().getSymbols().get(ruleParsingPosition.getCurPosition()).isNonTerminal()) {
                terminalList.addAll(reconstructTerminalSymbols2(ruleListPositions));
            }

            return terminalList;
        }
    }


    /**
     * Mutates the grammar so that the repeatingPatterns will be replaced by infinite rules
     *
     * @param repeatingPattern
     * @param modifyRule
     * @param modifyPosition
     */
    private void replaceRuleWithInfiniteRepetitions(GrammarMutatorUtils.RepeatingPattern repeatingPattern, Rule modifyRule, int modifyPosition) {
        List<Symbol> nextSymbols = new ArrayList<>(modifyRule.getSymbols().subList(modifyPosition, modifyRule.getSymbols().size()));

        // find the pattern that is found in current modify position
//        for (GrammarMutatorUtils.RepeatingPattern repeatingPattern : repeatingPatterns) {
        if (nextSymbols.size() >= repeatingPattern.getPattern().size()) {
            int checkPatternIdx = 0;
            while (checkPatternIdx < repeatingPattern.getPattern().size() && nextSymbols.get(checkPatternIdx).isTerminal() &&
                    nextSymbols.get(checkPatternIdx).getName().equals(repeatingPattern.getPattern().get(checkPatternIdx))) {
                checkPatternIdx++;
            }
            if (checkPatternIdx >= repeatingPattern.getPattern().size()) {

                // check if there already is an infinite rule in the modfyRule with the same pattern
                if (modifyRule.containsInfiniteRule()) {
                    // search for recursive nonTerminal
                    for (Symbol symbol : modifyRule.getSymbols()) {
                        if (symbol.isNonTerminal() && ((NonTerminal) symbol).containsRecurisveRule()) {
                            for (Rule rule : ((NonTerminal) symbol).getRules()) {
                                if (rule.isRecursiveRule()) {
                                    List<Terminal> pattern = rule.getRecursivePattern();
                                    if (pattern.stream().map(terminal -> terminal.getValue()).collect(toList()).equals(repeatingPattern.getPattern())) {
                                        // the recursive rule can be before or after the modifyPosition,
                                        // anyway mutation need to ensure that it will get to the lowest index

                                        if (modifyRule.getSymbols().indexOf(symbol) > modifyPosition) {
                                            // ensure that there are only tokens of the pattern between the symbol and modifyPosition
                                            // and remove those tokens

                                            int patternIdx = 0;
                                            int i = modifyPosition;
                                            while (i <= modifyRule.getSymbols().indexOf(symbol)
                                                    && modifyRule.getSymbols().get(i).getName().equals(repeatingPattern.getPattern().get(patternIdx))) {
                                                patternIdx++;
                                                patternIdx %= repeatingPattern.getPattern().size();
                                                i++;
                                            }
                                            if (i == modifyRule.getSymbols().indexOf(symbol)) {
                                                // only tokens included in the pattern where found
                                                // simply remove those tokens from the rule

                                                modifyRule.deleteSymbols(modifyPosition, modifyRule.getSymbols().indexOf(symbol) - modifyPosition);
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // find nr of symbols to be replaced
                int curSymbolIdx = 0;
                while (nextSymbols.size() > curSymbolIdx && nextSymbols.get(curSymbolIdx).isTerminal() &&
                        nextSymbols.get(curSymbolIdx).getName().equals(repeatingPattern.getPattern().get(curSymbolIdx % repeatingPattern.getPattern().size()))) {
                    curSymbolIdx++;
                }
                curSymbolIdx -= curSymbolIdx % repeatingPattern.getPattern().size();

                // replace symbols from modifyPosition to curSymbolIdx by new NonTerminal symbol
                NonTerminal infiniteNonTerminal = NonTerminalFactory.createNewNonTerminal();
//                infiniteNonTerminal.setRecursionNonTerminal(true);
                Rule infiniteRule = new Rule(repeatingPattern.getPattern().stream().map(pattern -> new Terminal(pattern, pattern)).collect(toList()));
                infiniteRule.appendSymbol(infiniteNonTerminal);
                infiniteNonTerminal.addRule(infiniteRule);
                infiniteNonTerminal.addRule(new Rule(new ArrayList<>()));

                modifyRule.replaceSymbols(modifyPosition, curSymbolIdx, Arrays.asList(infiniteNonTerminal));

            }
        }
//        }
    }

    /**
     * Collects all symbols (Terminals and Nonterminals) follow the given rule + position in the grammar
     *
     * @param rootSymbol
     * @param modifyRule
     * @param modifyPosition
     * @param sampleSeperator
     * @return
     */
    private List<Symbol> getSymbolsFromGrammarAfterPosition(NonTerminal rootSymbol, Rule modifyRule, int modifyPosition, List<String> sampleSeperator) {
        List<Symbol> followedSymbols = new ArrayList<>();
        followedSymbols.addAll(modifyRule.getSymbols().subList(modifyPosition, modifyRule.getSymbols().size()));

        Rule curPositionRule = modifyRule;
        NonTerminal curNonTerminal;
        while (!curPositionRule.isEndingRule(sampleSeperator)) {
            curNonTerminal = curPositionRule.getParentNonTerminal();

            List<RuleParsingPosition> positions = GrammarMutatorUtils.findNonTerminalOccurencesInGrammar(rootSymbol, curNonTerminal);

            // TODO this may occur after an enhanced mutation fallback was perfomed!!
            if (positions.size() > 1) {
//                assert false;
            }

            curPositionRule = positions.get(0).getRule();
            followedSymbols.addAll(positions.get(0).getRule().getSymbols().subList(positions.get(0).getCurPosition() + 1, positions.get(0).getRule().getSymbols().size()));
        }

        return followedSymbols;
    }

    private void performDeleteMutation(List<Diff> diffList, Diff curDiff, NonTerminal rootSymbol, Stack<RuleParsingPosition> ruleStackPositions, List<String> tokenList) {

        // perform a replace mutation with an empty list as replace tokens
        Diff replaceDiff = new Diff(Diff.Operation.REPLACE, curDiff.getIndex(), curDiff.getValues(), new ArrayList<>());
        performReplaceMutation(diffList, replaceDiff, rootSymbol, ruleStackPositions, tokenList, true);
    }

    private void performReplaceMutation(List<Diff> diffList, Diff curDiff, NonTerminal rootSymbol, Stack<RuleParsingPosition> ruleStackPositions, List<String> tokenList, boolean searchForRule) {

        Rule modifyRule = ruleStackPositions.peek().getRule();
        Integer modifyPosition = ruleStackPositions.peek().getCurPosition();

        // align replace string and modifyPosition: i.e. remove tokens that were already parsed successfully
        curDiff = removeParsedPrefixTokens2(diffList, curDiff, tokenList, rootSymbol);

        // small align diff here, i.e. remove already parsed tokens, insert tokens that need to be deleted
        curDiff = alignDiffWithParserError(diffList, curDiff, tokenList, rootSymbol, searchForRule);

        // store required information to local variables
        List<String> replaceTokens = curDiff.getReplaceValues();
        List<String> newTokens = curDiff.getValues();

        int diffIdx = 0;
        while (replaceTokens.size() > diffIdx && newTokens.size() > 0 && newTokens.size() > diffIdx &&
                replaceTokens.get(diffIdx).equals(newTokens.get(diffIdx))) {
            diffIdx++;
        }
        replaceTokens = replaceTokens.subList(diffIdx, replaceTokens.size());
        newTokens = newTokens.subList(diffIdx, newTokens.size());
        curDiff.setReplaceValues(replaceTokens);
        curDiff.setValues(newTokens);
        curDiff.setIndex(curDiff.getIndex() + diffIdx);


        // find correct rule in rule stack that matches the replace tokens
        List<Symbol> modifySubRule;

        if (modifyPosition == 0 && searchForRule) {
            RuleParsingPosition modifyRuleParsingPosition = findReplaceRule(replaceTokens, ruleStackPositions, diffList, curDiff, tokenList, rootSymbol);
            modifyRule = modifyRuleParsingPosition == null ? null : modifyRuleParsingPosition.getRule();
            modifyPosition = modifyRuleParsingPosition == null ? null : modifyRuleParsingPosition.getCurPosition();
            if (modifyRule != null) {
                modifySubRule = modifyRule.getSymbols();
            } else {
                modifyRule = ruleStackPositions.peek().getRule();
                modifyPosition = ruleStackPositions.peek().getCurPosition();
                modifySubRule = Arrays.asList(new Symbol[]{modifyRule.getParentNonTerminal()});
            }
        } else {
            modifySubRule = modifyRule.getSymbols().subList(modifyPosition, modifyRule.getSymbols().size());
        }

        // check if there are repeating patterns in the new values
        List<GrammarMutatorUtils.RepeatingPattern> newValuesPatterns = GrammarMutatorUtils.computeShortestRepeatedPatterns(newTokens, true);
        List<GrammarMutatorUtils.RepeatingPattern> replaceValuesPatterns = GrammarMutatorUtils.computeShortestRepeatedPatterns(replaceTokens, true);
        newValuesPatterns = newValuesPatterns.stream().filter(pattern -> pattern.getNrRepetitions() > ParameterSettings.MIN_NR_REPETITIONS).collect(toList());
        replaceValuesPatterns = replaceValuesPatterns.stream().filter(pattern -> pattern.getNrRepetitions() > ParameterSettings.MIN_NR_REPETITIONS).collect(toList());

        if (newValuesPatterns.size() > 0 || replaceValuesPatterns.size() > 0) {
            //TODO [12.10] implement this case -> so that an infinite rule will be added

            // additionally take care of cases were there are repetitions in replace + new values

            if (replaceValuesPatterns.size() > 0 && replaceValuesPatterns.get(0).getPattern().size() * replaceValuesPatterns.get(0).getNrRepetitions() >= replaceTokens.size()) {
                // all replace tokens are one repeating pattern

                if (allReplaceTokensInRule(replaceTokens, modifySubRule)) {
                    for (GrammarMutatorUtils.RepeatingPattern replaceValuesPattern : replaceValuesPatterns) {
                        replaceRuleWithInfiniteRepetitions(replaceValuesPattern, modifyRule, modifyPosition);
                        return;
                    }
                }
            }
        }

        if (allReplaceTokensInRule(replaceTokens, modifySubRule)) {

            // optimization: in case all symbols of the rule should be replaced and the rule is not the rootRule, just add a new alternative
            // TODO [PRIO] attention! this condition is not correct!
            if (!rootSymbol.getRules().get(0).equals(modifyRule) && modifyRule.getSymbols().size() == replaceTokens.size()) {
                modifyRule.getParentNonTerminal().addRule(new Rule(newTokens.stream().map((t) -> new Terminal(t, t)).collect(toList())));
            } else {

                // create  new NonTerminal symbol with two rules
               /*
               List<Symbol> origSymbols = new ArrayList<>();
                origSymbols.addAll(modifyRule.getSymbols().subList(modifyPosition, modifyPosition + replaceTokens.size()));
                */
                // TODO this case is not handled correctly in some special cases, what if in the "getAllOrigSymbols" set not all rules are included ?
                // TODO e.g.: root sample: STP_FACE_OUTER_BOUND and to be added: axis placement

                List<Symbol> origSymbols = getAllOrigSymbolsFromRule(replaceTokens,
                        modifyRule.getSymbols().subList(modifyPosition, modifyRule.getSymbols().size()));
                NonTerminal newNonTerminal = NonTerminalFactory.createNewNonTerminal(new Rule(origSymbols));

                Rule newSymbolsRule = new Rule(newTokens.stream().map((t) -> new Terminal(t, t)).collect(toList()));

                // check for repeating patterns in new values
                if (newValuesPatterns.size() > 0 && newValuesPatterns.get(0).getPattern().size() * newValuesPatterns.get(0).getNrRepetitions() >= newTokens.size()) {
                    // all new tokens are one repeating pattern
                    for (GrammarMutatorUtils.RepeatingPattern replaceValuesPattern : replaceValuesPatterns) {
                        replaceRuleWithInfiniteRepetitions(replaceValuesPattern, modifyRule, modifyPosition);
                    }
                }


                newNonTerminal.addRule(newSymbolsRule);

                // replace origSymbols with new nonTerminal symbol
                modifyRule.replaceSymbols(modifyPosition, origSymbols.size(), Collections.singletonList(newNonTerminal));
            }
        } else {
            // the replace string is not in one single rule

            if (replaceTokens.size() == newTokens.size()) {
                // if replace and new token is the same size, split at rule length

                if (replaceTokens.size() < modifyRule.getSymbols().size() - modifyPosition) {
                    // TODO [QUICK FIX] handle such cases correctly, for now only enforce fallback diff
                    // modifyRule and replaceTokens do not match, so no safe mutation is possible -> return and enforce fallback mutation
                    return;
                }

                Diff firstSplit = new Diff(Diff.Operation.REPLACE,
                        curDiff.getIndex(),
                        replaceTokens.subList(0, modifyRule.getSymbols().size() - modifyPosition),
                        newTokens.subList(0, modifyRule.getSymbols().size() - modifyPosition));
                Diff secondSplit = new Diff(Diff.Operation.REPLACE,
                        curDiff.getIndex() + firstSplit.getReplaceValues().size(),
                        replaceTokens.subList(firstSplit.getReplaceValues().size(), replaceTokens.size()),
                        newTokens.subList(firstSplit.getValues().size(), newTokens.size()));
                performReplaceMutation(diffList, firstSplit, rootSymbol, ruleStackPositions, tokenList, true);
                boolean parserErrorFound = genericInternalParser.findParseError(ruleStackPositions, tokenList, rootSymbol, true);
                if (parserErrorFound) {
                    performReplaceMutation(diffList, secondSplit, rootSymbol, ruleStackPositions, tokenList, true);
                }
            } else if (replaceTokens.size() >= 2 && newTokens.size() == 0) {
                // special case for delete diff
                int firstSplitLength = getIndexOfFirstNonTerminal(modifyRule.getSymbols().subList(modifyPosition, modifyRule.getSymbols().size()));
                firstSplitLength = firstSplitLength > replaceTokens.size() ? replaceTokens.size() : firstSplitLength;

                Diff firstSplit = new Diff(Diff.Operation.REPLACE, curDiff.getIndex(), replaceTokens.subList(0, firstSplitLength), new ArrayList<>());
                Diff secondSplit = new Diff(Diff.Operation.REPLACE, curDiff.getIndex() + firstSplit.getReplaceValues().size(), replaceTokens.subList(firstSplit.getReplaceValues().size(), replaceTokens.size()), new ArrayList<>());
                performReplaceMutation(diffList, firstSplit, rootSymbol, ruleStackPositions, tokenList, true);
                if (ParameterSettings.DEBUG_OUTPUT)
                    System.out.println("> Performed delete mutation: " + ruleStackPositions.peek().getRule());
                ruleStackPositions = new Stack<>();
                boolean parserErrorFound = genericInternalParser.findParseError(ruleStackPositions, tokenList, rootSymbol, true);
                if (parserErrorFound) {
                    performReplaceMutation(diffList, secondSplit, rootSymbol, ruleStackPositions, tokenList, true);
                }
            } else {
                // more enhanced checks are necessary

                if (modifyPosition == 0 && replaceTokens.size() > modifyRule.getSymbols().size() - modifyPosition && ruleStackPositions.size() >= 2) {
                    // modifyRule would be replaced completely, so simply do replace operation on the parent nonTerminal
                    ruleStackPositions.pop();
                    performReplaceMutation(diffList, curDiff, rootSymbol, ruleStackPositions, tokenList, false);
                } else if (modifyRule.getParentNonTerminal().getRules().size() == 1) {
                    // rule can simply be replaced and substituted in its parent rule
                    substituteNonTerminalInGrammar(rootSymbol, modifyRule);
                    // after substitution try again to mutate in the next rule of the stack (and update the curPosition)
                    if (ruleStackPositions.size() > 1) {
                        ruleStackPositions.pop();
//                        ruleStackPositions.peek().setCurPosition(ruleStackPositions.peek().getCurPosition() + modifyPosition);  /* commented out because somehow curPosition became higher than rule length */
                        performReplaceMutation(diffList, curDiff, rootSymbol, ruleStackPositions, tokenList, true);
                    } else {

                        if (replaceTokens.size() == 1) {
                            // rule cannot be splitted, bad case, but need to search for replace token in previously parsed symbols
                            Rule newModifyRule = getBestModifyRuleFromPreviousRules(modifyRule, modifyPosition, replaceTokens);
                            if (newModifyRule == null) {
                                if(ParameterSettings.INFO_OUTPUT)
                                    System.out.println("ATTENTION! COULD NOT PERFORM REPLACE MUTATION: " + curDiff);
                                return;
                            }
                            ruleStackPositions.push(new RuleParsingPosition(newModifyRule, newModifyRule.getSymbols().indexOf(new Terminal(replaceTokens.get(0), replaceTokens.get(0)))));
                            performReplaceMutation(diffList, curDiff, rootSymbol, ruleStackPositions, tokenList, true);
                        } else {
                            // get split position, i.e. the tokens that can be parsed
                            int nrReplaceableTokens = getNrReplacableTokens(replaceTokens, modifyRule.getSymbols().subList(modifyPosition, modifyRule.getSymbols().size()));
                            if (nrReplaceableTokens < replaceTokens.size()) {
                                // split at nrReplaceableTokens
                                Diff firstSplit = new Diff(Diff.Operation.REPLACE,
                                        curDiff.getIndex(),
                                        replaceTokens.subList(0, nrReplaceableTokens),
                                        newTokens.subList(0, newTokens.size() < nrReplaceableTokens ? newTokens.size() : nrReplaceableTokens));
                                Diff secondSplit = new Diff(Diff.Operation.REPLACE,
                                        curDiff.getIndex() + firstSplit.getReplaceValues().size(),
                                        replaceTokens.subList(firstSplit.getReplaceValues().size(), replaceTokens.size()),
                                        newTokens.subList(firstSplit.getValues().size(), newTokens.size()));
                                performReplaceMutation(diffList, firstSplit, rootSymbol, ruleStackPositions, tokenList, true);
                                ruleStackPositions = new Stack<>();
                                genericInternalParser.findParseError(ruleStackPositions, tokenList, rootSymbol, true);
                                performReplaceMutation(diffList, secondSplit, rootSymbol, ruleStackPositions, tokenList, true);

                            } else {
                                // TODO [PRIO] implement this case
                                if(ParameterSettings.INFO_OUTPUT)
                                    System.out.println("ATTENTION: REPLACE case not yet implemented!!!!");
                            }
                        }
                    }

                } else {
                    //  handle case where the rule cannot be simply substituted, but splitted
                    //  because it has more than one rule

                    // need to find the rule that matches the replace tokens
                    if (modifyRule.getSymbols().get(modifyPosition).isNonTerminal()) {
                        NonTerminal modifyNonTerminal = (NonTerminal) modifyRule.getSymbols().get(modifyPosition);
                        for (Rule rule : modifyNonTerminal.getRules()) {
                            if (rule.getSymbols().size() > 0 && rule.getSymbols().get(0).getName().equals(replaceTokens.get(0))) {
                                Diff firstSplit = new Diff(Diff.Operation.REPLACE,
                                        curDiff.getIndex(),
                                        replaceTokens.subList(0, 1),
                                        newTokens.subList(0, 1));
                                Diff secondSplit = new Diff(Diff.Operation.REPLACE,
                                        curDiff.getIndex() + firstSplit.getReplaceValues().size(),
                                        replaceTokens.subList(firstSplit.getReplaceValues().size(), replaceTokens.size()),
                                        newTokens.subList(firstSplit.getValues().size(), newTokens.size()));
                                ruleStackPositions.push(new RuleParsingPosition(rule, 0));
                                performReplaceMutation(diffList, firstSplit, rootSymbol, ruleStackPositions, tokenList, true);
                                ruleStackPositions = new Stack<>();
                                genericInternalParser.findParseError(ruleStackPositions, tokenList, rootSymbol, true);
                                performReplaceMutation(diffList, secondSplit, rootSymbol, ruleStackPositions, tokenList, true);
                                return;
                            }
                        }
                    } else {
                        // curSymbol is terminal
                        // split on basis of replace tokens
                        int firstSplitLength = modifyRule.getSymbols().size() - modifyPosition;

                        Diff firstSplit = new Diff(Diff.Operation.REPLACE,
                                curDiff.getIndex(),
                                replaceTokens.subList(0, firstSplitLength < replaceTokens.size() ? firstSplitLength : replaceTokens.size()),
                                newTokens.subList(0, firstSplitLength < newTokens.size() ? firstSplitLength : newTokens.size()));
                        Diff secondSplit = new Diff(Diff.Operation.REPLACE,
                                curDiff.getIndex() + firstSplit.getReplaceValues().size(),
                                replaceTokens.subList(firstSplit.getReplaceValues().size(), replaceTokens.size()),
                                newTokens.subList(firstSplit.getValues().size(), newTokens.size()));
                        performReplaceMutation(diffList, firstSplit, rootSymbol, ruleStackPositions, tokenList, true);
                        ruleStackPositions = new Stack<>();
                        genericInternalParser.findParseError(ruleStackPositions, tokenList, rootSymbol, true);
                        performReplaceMutation(diffList, secondSplit, rootSymbol, ruleStackPositions, tokenList, true);
                        return;
                    }
                }
            }
        }
    }

    private RuleParsingPosition findReplaceRule(List<String> replaceTokens, Stack<RuleParsingPosition> ruleStackPositions, List<Diff> initialDiffList, Diff replaceDiff, List<String> tokenList, NonTerminal rootSymbol) {
        if (ParameterSettings.MATCH_DIFF_TO_FIND_MODIFY_RULE) {
            RuleParsingPosition modifyRuleParsingPosition = findReplaceRuleByMatchingDiff(replaceTokens, ruleStackPositions, initialDiffList, replaceDiff, tokenList, rootSymbol);
            if (modifyRuleParsingPosition != null) {
                return modifyRuleParsingPosition;
            }

        }

        RuleParsingPosition curPosition = ruleStackPositions.peek();
        int idx = ruleStackPositions.size() - 1;
        while (idx >= 0 && curPosition.getCurPosition() == 0) {
            curPosition = ruleStackPositions.get(idx);
            idx--;
        }

        for (Symbol symbol : curPosition.getRule().getSymbols().subList(curPosition.getCurPosition(), curPosition.getRule().getSymbols().size())) {

            // remove already parsed tokens
            replaceTokens = removeParsedPrefixTokens(initialDiffList, replaceDiff, tokenList, rootSymbol).getReplaceValues();
            /*int diffIdx = 0;
            while(replaceTokens.size() > diffIdx && replaceDiff.getValues().size() > 0 &&
                    replaceTokens.get(diffIdx).equals(replaceDiff.getValues().get(diffIdx))) {
                diffIdx++;
            }
            replaceTokens = replaceTokens.subList(diffIdx, replaceTokens.size());
*/

            if (symbol.isNonTerminal() && replaceTokens.size() > 0) {
                Rule replaceRule = findReplaceRule(replaceTokens.get(0), (NonTerminal) symbol);
                return new RuleParsingPosition(replaceRule, findModifyPositionInRule(replaceRule, replaceTokens));
            } else if (replaceTokens.size() > 0 && symbol.getName().equals(replaceTokens.get(0))) {
                return new RuleParsingPosition(curPosition.getRule(), findModifyPositionInRule(curPosition.getRule(), replaceTokens));
            }
        }

        // TODO [11.7] use findReplaceRuleByMatchingDiff as fallback here
        return null;
    }

    /**
     * Uses the diff values to find the modify rule by first matching the base rule and finding the rule to mutate with the parsed rules list
     *
     * @return
     */
    private RuleParsingPosition findReplaceRuleByMatchingDiff(List<String> replaceTokens, Stack<RuleParsingPosition> ruleStackPositions, List<Diff> initialDiffList, Diff replaceDiff, List<String> tokenList, NonTerminal rootSymbol) {

        // check input conditions
        for (int i = 0; i < replaceDiff.getReplaceValues().size(); i++) {
            if (!replaceTokens.get(i).equals(replaceDiff.getReplaceValues().get(i))) {
                assert (false);
            }
        }

        // build base diff tokenlist and build baseRuleList
        List<String> baseTokenList = new ArrayList<>();
        Stack<RuleParsingPosition> baseRuleStackPositions = new Stack<>();
        List<RuleParsingPosition> baseRuleList = new ArrayList<>();

        for (Diff diff : initialDiffList) {
            switch (diff.getOperation()) {
                case EQUAL:
                case DELETE:
                    baseTokenList.addAll(diff.getValues());
                    break;
                case REPLACE:
                    baseTokenList.addAll(diff.getReplaceValues());
                    break;
            }
        }

        boolean parseError = genericInternalParser.parseNonTerminal(rootSymbol, new ArrayList<>(baseTokenList), true, baseRuleStackPositions, baseRuleList, true);
//        assert (!parseError);

        // search for replace tokens in parsed rules list in a stack wise fashion
        List<RuleParsingPosition> possibleResults = new ArrayList<>();

        findReplaceRuleByBasedRuleList(new ArrayList<>(baseRuleList), null, 0, replaceTokens, possibleResults);

        // find correct result in possible results
        if (possibleResults.size() == 0) {
            // TODO remove this check !
            return null;
        }
        if (possibleResults.size() <= 1) {
            return new RuleParsingPosition(possibleResults.get(0).getRule(), findModifyPositionInRule(possibleResults.get(0).getRule(), replaceTokens));
        } else {
            int foundReplaceMatches = 0;
            int curTokenMatches = 0;

            for (String baseToken : baseTokenList) {
                if (replaceDiff.getIndex() <= baseTokenList.indexOf(baseToken)) {
                    if (foundReplaceMatches >= possibleResults.size()) {
                        return new RuleParsingPosition(possibleResults.get(possibleResults.size() - 1).getRule(), findModifyPositionInRule(possibleResults.get(possibleResults.size() - 1).getRule(), replaceTokens));
                    }
                    return new RuleParsingPosition(possibleResults.get(foundReplaceMatches).getRule(), findModifyPositionInRule(possibleResults.get(foundReplaceMatches).getRule(), replaceTokens));
                }
                if (baseToken.equals(replaceTokens.get(curTokenMatches))) {
                    curTokenMatches++;
                    if (curTokenMatches >= replaceTokens.size()) {
                        foundReplaceMatches++;
                        curTokenMatches = 0;
                    }
                } else {
                    curTokenMatches = 0;
                }
            }
            // TODO find correct result among the possibleResults

            return null;
        }

    }

    private int findModifyPositionInRule(Rule modifyRule, List<String> replaceTokens) {
        int idx = 0;
        if (modifyRule == null) {
            return 0;
        }
        // TODO what if there are multiple symbols that match the first replaceToken???!
        for (Symbol symbol : modifyRule.getSymbols()) {
            if (symbol.isNonTerminal() && ((NonTerminal) symbol).getTerminalBeginningValues().contains(replaceTokens.get(0))) {
                return idx;
            } else if (symbol.getName().equals(replaceTokens.get(0))) {
                return idx;
            }
            idx++;
        }

        return 0;
    }

    private int findReplaceRuleByBasedRuleList(List<RuleParsingPosition> baseRuleList, RuleParsingPosition curMatchedRulePosition, int matchedTokens, List<String> replaceTokens, List<RuleParsingPosition> possibleResults) {

        if (baseRuleList.size() <= 0) {
            return 0;
        }

        Rule curRule = baseRuleList.get(0).getRule();
        baseRuleList.remove(0);

        for (Symbol symbol : curRule.getSymbols()) {
            if (symbol.isTerminal()) {
                if (replaceTokens.size() > matchedTokens && symbol.getName().equals(replaceTokens.get(matchedTokens))) {
                    if (curMatchedRulePosition == null) {
                        curMatchedRulePosition = new RuleParsingPosition(curRule, curRule.getSymbols().indexOf(symbol));
                    }
                    matchedTokens++;
                    if (matchedTokens >= replaceTokens.size()) {
                        possibleResults.add(curMatchedRulePosition);
                        curMatchedRulePosition = null;
                        matchedTokens = 0;
                    }
                } else {
                    curMatchedRulePosition = null;
                    matchedTokens = 0;
                }
            } else if (symbol.isNonTerminal()) {
                matchedTokens = findReplaceRuleByBasedRuleList(baseRuleList, curMatchedRulePosition, matchedTokens, replaceTokens, possibleResults);
            }
        }
        return matchedTokens;
    }


    private Rule findReplaceRule(String s, NonTerminal symbol) {
        for (Rule rule : symbol.getRules()) {
            if (rule.getSymbols().size() > 0) {
                Symbol firstSymbol = rule.getSymbols().get(0);
                if (firstSymbol.isTerminal() && firstSymbol.getName().equals(s)) {
                    return rule;
                } else if (firstSymbol.isNonTerminal()) {
                    Rule recRule = findReplaceRule(s, (NonTerminal) firstSymbol);
                    if (recRule != null) return recRule;
                }
            }
        }
        return null;

    }

    private Diff alignDiffWithParserError(List<Diff> diffList, Diff diff, List<String> tokenList, NonTerminal rootSymbol, boolean searchForRule) {

        List<String> replaceTokens = diff.getReplaceValues();
        List<String> newTokens = diff.getValues();

        Stack<RuleParsingPosition> ruleStackPositions = new Stack<>();
        List<RuleParsingPosition> ruleList = new ArrayList<>();

        // get list of parsed rules
        if (!genericInternalParser.parseNonTerminal(rootSymbol, new ArrayList<>(tokenList), false, ruleStackPositions, ruleList, true)) {
            // parsed without an error
            return null;
        }

        RuleParsingPosition curRulePosition = ruleStackPositions.peek();
        Rule modifyRule = curRulePosition.getRule();

        // find correct rule in rule stack that matches the replace tokens
        if (curRulePosition.getCurPosition() == 0 && searchForRule) {
            RuleParsingPosition modifyRuleStackPosition = findReplaceRule(replaceTokens, ruleStackPositions, diffList, diff, tokenList, rootSymbol);
            modifyRule = modifyRuleStackPosition == null ? null : modifyRuleStackPosition.getRule();
            if (modifyRule == null) {
                modifyRule = ruleStackPositions.peek().getRule();
            }
        }

        // build list of next terminals (as long as there are not nonTerminals with two rules
        List<Symbol> nextSymbols = modifyRule.getSymbols().subList(curRulePosition.getCurPosition(), modifyRule.getSymbols().size());
        List<String> nextTerminals = new ArrayList<>();

        for (Symbol curSymbol : nextSymbols) {
            if (curSymbol.isTerminal()) {
                nextTerminals.add(curSymbol.getName());
            } else {
                List<String> curNextTerminals = (((NonTerminal) curSymbol).getTerminalSymbolsValues());
                if (curNextTerminals == null) {
                    break;
                }
                nextTerminals.addAll(curNextTerminals);
            }
        }

        if (replaceTokens.size() > 0 && nextTerminals.size() > 0 && replaceTokens.indexOf(nextTerminals.get(0)) != 0) {
            int insertCount = nextTerminals.indexOf(replaceTokens.get(0));
            for (int i = 0; i < insertCount; i++) {
                replaceTokens.add(i, nextTerminals.get(i));
            }
        }

        return diff;
    }

    /**
     * Remove those tokens from the replaceTokens list, that were already parsed successfully
     *
     * @param diffList
     * @param diff
     * @param tokenList
     * @param rootSymbol
     * @return
     */
    private Diff removeParsedPrefixTokens(List<Diff> diffList, Diff diff, List<String> tokenList, NonTerminal rootSymbol) {

        List<String> replaceTokens = diff.getReplaceValues();
        List<String> newTokens = diff.getValues();

        Stack<RuleParsingPosition> ruleStackPositions = new Stack<>();
        List<RuleParsingPosition> ruleList = new ArrayList<>();

        // get list of parsed rules
        if (!genericInternalParser.parseNonTerminal(rootSymbol, new ArrayList<>(tokenList), false, ruleStackPositions, ruleList, true)) {
            // parsed without an error
            return null;
        }

        // build list of next terminals (as long as there are not nonTerminals with two rules
        RuleParsingPosition curRule = ruleStackPositions.peek();
        List<Symbol> nextSymbols = curRule.getRule().getSymbols().subList(curRule.getCurPosition(), curRule.getRule().getSymbols().size());
        List<String> nextTerminals = new ArrayList<>();

        for (Symbol curSymbol : nextSymbols) {
            if (curSymbol.isTerminal()) {
                nextTerminals.add(curSymbol.getName());
            } else {
                List<String> curNextTerminals = (((NonTerminal) curSymbol).getTerminalSymbolsValues());
                if (curNextTerminals == null) {
                    break;
                }
                nextTerminals.addAll(curNextTerminals);
            }
        }

        if (nextTerminals.size() > 1) {

            // find matching terminals in the replaceList
            int matchingCount = 0;
            int matchingIdx = 0;
            int curIdx = 0;

            // find first symbols matching replaceTokens and nextTerminals
            int countMatches = CollectionUtils.countMatches(replaceTokens, object -> object.equals(nextTerminals.get(0)));

            int minMatchCount = replaceTokens.size() < nextTerminals.size() ? replaceTokens.size() : nextTerminals.size();
            for (String replaceToken : replaceTokens) {

                if (matchingCount >= minMatchCount) {
                    break;
                }

                if (matchingCount > 0 && matchingCount < nextTerminals.size()) {
                    if (replaceToken.equals(nextTerminals.get(matchingCount))) {
                        matchingCount++;
                    } else {
                        matchingCount = 0;
                    }
                } else {
                    if (replaceToken.equals(nextTerminals.get(0))) {
                        matchingIdx = curIdx;
                        matchingCount++;
                    }
                }
                curIdx++;
            }

            if (matchingCount < countMatches) {
                matchingIdx = 0;
            }

            // delete first matchingIdx tokens from replaceTokens
            for (int i = 0; i < matchingIdx; i++) {

                String removeToken = replaceTokens.get(0);


                // delete newValueTokens if they match the replacTokens
                if (newTokens.size() > 0 && newTokens.get(0).equals(replaceTokens.get(0))) {
                    newTokens.remove(0);
                }
                replaceTokens.remove(0);

                // TODO [risky optmization] -> maybe a safer and esier way can be found
                if (matchingIdx == 1
                        && nextTerminals.size() > replaceTokens.size()
                        && nextTerminals.get(replaceTokens.size()).equals(removeToken)) {
                    replaceTokens.add(removeToken);
                }
            }
        }

        return diff;
    }

    /**
     * INFO: this method removes the parsed tokens based on the parsedRulesList
     * Remove those tokens from the replaceTokens list, that were already parsed successfully
     *
     * @param diffList
     * @param diff
     * @param tokenList
     * @param rootSymbol
     * @return
     */
    private Diff removeParsedPrefixTokens2(List<Diff> diffList, Diff diff, List<String> tokenList, NonTerminal rootSymbol) {

        Diff modifyDiff = diff.getDeepCopy();

        Stack<RuleParsingPosition> ruleStackPositions = new Stack<>();
        List<RuleParsingPosition> parsedRulesList = new ArrayList<>();

        boolean parseError = genericInternalParser.parseNonTerminal(rootSymbol, tokenList, false, ruleStackPositions, parsedRulesList, true);

        List<String> parsedTokens = new ArrayList<>();

        parsedTokens = inductParsedTokens2(new ArrayList<>(parsedRulesList)).stream().map(token -> token.getName()).collect(toList());

        /*
        parsedRulesList.forEach(ruleParsingPosition -> parsedTokens.addAll(ruleParsingPosition.getRule().getSymbols().subList(0, ruleParsingPosition.getCurPosition()).stream()
                .filter(s -> s.isTerminal()).map(s -> s.getName()).collect(Collectors.<String>toList())));
*/
        int parsedTokensIdx = parsedTokens.size() - 1;
        int diffIdx = modifyDiff.getValues().size() - 1;
        for (int i = modifyDiff.getValues().size() - 1; i >= 0; i--) {
            if (parsedTokensIdx > 0 && modifyDiff.getValues().get(diffIdx).equals(parsedTokens.get(parsedTokensIdx))) {
                parsedTokensIdx--;
            } else {
                parsedTokensIdx = parsedTokens.size() - 1;
            }
            diffIdx--;
        }

        if (parsedTokensIdx < parsedTokens.size() - 1) {
            modifyDiff.setValues(modifyDiff.getValues().subList(parsedTokens.size() - parsedTokensIdx - 1, modifyDiff.getValues().size()));
        }

        return modifyDiff;
    }

    private List<String> inductParsedTokens(List<RuleParsingPosition> parsedRulesList) {
        List<String> parsedTokens = new ArrayList<>();

        while (!parsedRulesList.isEmpty()) {
            RuleParsingPosition curRuleParsingPosition = parsedRulesList.get(0);
            parsedRulesList.remove(0);
            for (Symbol symbol : curRuleParsingPosition.getRule().getSymbols().subList(0, curRuleParsingPosition.getCurPosition())) {
                if (symbol.isTerminal()) {
                    parsedTokens.add(symbol.getName());
                } else {
                    parsedTokens.addAll(inductParsedTokens(parsedRulesList));
                }
            }

        }
        return parsedTokens;
    }

    private List<String> inductParsedTokens(Stack<RuleParsingPosition> parsedRulesStack) {
        List<String> parsedTokens = new ArrayList<>();

        for (RuleParsingPosition ruleParsingPosition : parsedRulesStack) {
            parsedTokens.addAll(ruleParsingPosition.getRule().getSymbols().subList(0, ruleParsingPosition.getCurPosition())
                    .stream().filter(s -> s.isTerminal()).map(s -> s.getName()).collect(toList()));
        }

        return parsedTokens;
    }

    /**
     * Tries to find the best matching replaceTokens in previously parsed rules
     *
     * @param modifyRule
     * @param modifyPosition
     * @param replaceTokens
     */
    private Rule getBestModifyRuleFromPreviousRules(Rule modifyRule, int modifyPosition, List<String> replaceTokens) {
        if(ParameterSettings.INFO_OUTPUT)
            System.out.println("ATTENTION! error prone mutation will happen, be aware of that!");

        Rule newModifyRule;
        while (modifyPosition > 0) {

            if (modifyRule.getSymbols().get(modifyPosition).isNonTerminal()) {
                NonTerminal newModifyNonTerminal = (NonTerminal) modifyRule.getSymbols().get(modifyPosition);
                // search in rule at current position
                for (Rule rule : newModifyNonTerminal.getRules()) {
                    if (allReplaceTokensInRule(replaceTokens, rule.getSymbols())) {
                        return rule;
                    }
                }
                // search in rule at current position recursively
                for (Rule rule : newModifyNonTerminal.getRules()) {
                    newModifyRule = getBestModifyRuleFromPreviousRules(rule, rule.getSymbols().size() - 1, replaceTokens);
                    if (newModifyRule != null) {
                        return newModifyRule;
                    }
                }
            }
            // try another position
            modifyPosition--;
        }

        // could not find any match
        return null;
    }

    private void substituteNonTerminalInGrammar(NonTerminal rootSymbol, Rule modifyRule) {
        rootSymbol.replaceNonTerminalWithSymbolsRecursive(modifyRule.getParentNonTerminal(), modifyRule.getSymbols());
    }

    private boolean allReplaceTokensInRule(List<String> replaceTokens, List<Symbol> symbols) {

        return getNrReplacableTokens(replaceTokens, symbols) == replaceTokens.size();
    }

    private int getNrReplacableTokens(List<String> replaceTokens, List<Symbol> symbols) {
        Stack<RuleParsingPosition> ruleStackPositions = new Stack<>();
        List<String> localReplaceTokens = new ArrayList<>(replaceTokens);
        for (int idx = 0; localReplaceTokens.size() > 0; idx++) {
            if (idx >= symbols.size()) {
                return replaceTokens.size() - localReplaceTokens.size();
            }
            if (symbols.get(idx).isTerminal()) {
                if (((Terminal) symbols.get(idx)).getValue().equals(localReplaceTokens.get(0))) {
                    localReplaceTokens.remove(0);
                } else {
                    return replaceTokens.size() - localReplaceTokens.size();
                }
            } else if (genericInternalParser.parseNonTerminal((NonTerminal) symbols.get(idx), localReplaceTokens, true, ruleStackPositions, new ArrayList<>(), true)) {
                return replaceTokens.size() - localReplaceTokens.size();
            }
            ruleStackPositions = new Stack<>();
        }
        return replaceTokens.size();
    }

    private List<Symbol> getAllOrigSymbolsFromRule(List<String> replaceTokens, List<Symbol> symbols) {

        Stack<RuleParsingPosition> ruleStackPositions = new Stack<>();
        List<String> localReplaceTokens = new ArrayList<>(replaceTokens);
        List<Symbol> origSymbols = new ArrayList<>();
        for (int idx = 0; localReplaceTokens.size() > 0; idx++) {
            if (symbols.get(idx).isTerminal()) {
                if (((Terminal) symbols.get(idx)).getValue().equals(localReplaceTokens.get(0))) {
                    localReplaceTokens.remove(0);
                } else {
                    return null;
                }
            } else {
                genericInternalParser.parseNonTerminal((NonTerminal) symbols.get(idx), localReplaceTokens, true, ruleStackPositions, new ArrayList<>(), true);
            }
            origSymbols.add(symbols.get(idx));
            ruleStackPositions = new Stack<>();
        }
        if (localReplaceTokens.size() > 0) return null;
        return origSymbols;
    }

    private Integer getIndexOfFirstNonTerminal(List<Symbol> symbols) {
        int idx = 0;
        for (Symbol symbol : symbols) {
            if (symbol.isNonTerminal()) {
                return idx;
            }
            idx++;
        }
        return symbols.size();
    }

    private void performInsertMutation(List<Diff> diffList, NonTerminal rootSymbol, Stack<RuleParsingPosition> ruleStackPositions, List<String> tokenList) {

        // store required information to local variables
        List<String> insertTokens = diffList.get(0).getValues();

        Rule modifyRule = ruleStackPositions.peek().getRule();
        int modifyPosition = ruleStackPositions.peek().getCurPosition();

        RuleParsingPosition nextRuleStackPosition = null;
        if (ruleStackPositions.size() >= 2) {
            nextRuleStackPosition = ruleStackPositions.get(ruleStackPositions.size() - 2);
        }

        // TODO implement this case also for insert mutations
        // find correct rule in rule stack that matches the replace tokens
        /*List<Symbol> modifySubRule;

        if (modifyPosition == 0 && searchForRule) {
            RuleParsingPosition modifyRuleParsingPosition = findReplaceRule(replaceTokens, ruleStackPositions, diffList, curDiff, tokenList, rootSymbol);
            modifyRule = modifyRuleParsingPosition == null ? null : modifyRuleParsingPosition.getRule();
            modifyPosition = modifyRuleParsingPosition == null ? null : modifyRuleParsingPosition.getCurPosition();
            if (modifyRule != null) {
                modifySubRule = modifyRule.getSymbols();
            } else {
                modifyRule = ruleStackPositions.peek().getRule();
                modifyPosition = ruleStackPositions.peek().getCurPosition();
                modifySubRule = Arrays.asList(new Symbol[]{modifyRule.getParentNonTerminal()});
            }
        } else {
            modifySubRule = modifyRule.getSymbols().subList(modifyPosition, modifyRule.getSymbols().size());
        }*/

        // Case parser did not recognize any token from the insertTokens
        if (modifyPosition < 1 || !insertTokens.contains(modifyRule.getSymbols().get(modifyPosition - 1))) {

            if (modifyPosition > 0 && modifyPosition < modifyRule.getSymbols().size()) {
                // Case R0 : a1 . a2

                List<String> nextTerminalBeginnings = modifyRule.getSymbols().get(modifyPosition).getTerminalBeginningValues();
                if (nextTerminalBeginnings.contains(diffList.get(1).getValues().get(0)) ||
                        // TODO [PRIO] fix this condition, as an index out of bounds may occur here
                        nextTerminalBeginnings.contains(diffList.get(0).getValues().get(diffList.get(0).getValues().size() - 1))) {
                    // in case the following tokens in the diff list are in a2

                    // insert new NonTerminal symbol with one emtpy rule and one insertToken rule
                    insertWithNewNonTerminalAndEmptyRule(insertTokens, modifyRule, modifyPosition);
                } else {
                    if (nextRuleStackPosition != null) {

                        if (nextRuleStackPosition.getRule().getSymbols().size() > (nextRuleStackPosition.getCurPosition() + 1) &&
                                nextRuleStackPosition.getRule().getSymbols().get(nextRuleStackPosition.getCurPosition() + 1).getTerminalBeginningValues().contains(diffList.get(1).getValues().get(0))) {
                            // in case the following tokens in the diff list are in the next rule

                            // insert new nonTerminal symbol with one insertToken rule and one a2 rule
                            insertWithNewNonTerminalAndOrigRule(insertTokens, modifyRule, modifyPosition);

                        } else {
                            // grammar cannot be changed in this rule, try with next one
                            ruleStackPositions.pop();
                            performInsertMutation(diffList, rootSymbol, ruleStackPositions, tokenList);
                        }
                    } else {
                        insertWithNewNonTerminalAndEmptyRule(insertTokens, modifyRule, modifyPosition);
                        if(ParameterSettings.INFO_OUTPUT)
                            System.out.println("ATTENTION risky change was performed");
                    }
                }

            } else if (modifyPosition > 0) {
                // Case R0: a1 .

                if (nextRuleStackPosition != null &&
                        nextRuleStackPosition.getRule().getSymbols().get(nextRuleStackPosition.getCurPosition() + 1).getTerminalBeginningValues().contains(diffList.get(1).getValues().get(0))) {
                    // in case the following tokens in the diff list are in the next rule

                    // insert new nonTerminal symbol with one insertToken rule and one a2 rule
                    insertWithNewNonTerminalAndOrigRule(insertTokens, modifyRule, modifyPosition);
                } else {
                    // grammar cannot be changed in this rule, try with next one
                    ruleStackPositions.pop();
                    performInsertMutation(diffList, rootSymbol, ruleStackPositions, tokenList);
                }

            } else {
                // Case R0: . a2

                if (modifyRule.getSymbols().get(modifyPosition).getTerminalBeginningValues().contains(diffList.get(1).getValues().get(0))) {
                    // in case the following tokens in the diff list are in a2

                    // insert new NonTerminal symbol with one emtpy rule and one insertToken rule
                    insertWithNewNonTerminalAndEmptyRule(insertTokens, modifyRule, modifyPosition);

                } else if (nextRuleStackPosition != null &&
                        nextRuleStackPosition.getRule().getSymbols().size() > nextRuleStackPosition.getCurPosition() + 1 &&
                        nextRuleStackPosition.getRule().getSymbols().get(nextRuleStackPosition.getCurPosition() + 1).getTerminalBeginningValues().contains(diffList.get(1).getValues().get(0))) {
                    // in case the following tokens in the diff list are in the next rule

                    //add new alternative to current rule ( -> R0: a2 R0: [insertTokens] )
                    modifyRule.getParentNonTerminal().addRule(new Rule(insertTokens.stream().map((t) -> new Terminal(t, t)).collect(toList())));


                } else {
                    // grammar cannot be changed in this rule, try with next one
                    ruleStackPositions.pop();
                    performInsertMutation(diffList, rootSymbol, ruleStackPositions, tokenList);
                }
            }

        } else {
            // TODO [PRIO] handle this case
            System.out.println("              INSERT CASE NOT YET IMPLEMENTED!!!!!!!!!!!!!!!");
        }

    }

    private void insertWithNewNonTerminalAndEmptyRule(List<String> insertTokens, Rule modifyRule, int modifyPosition) {
        List<Symbol> emptySymbols = new ArrayList<>();
        NonTerminal newNonTerminal = NonTerminalFactory.createNewNonTerminal(new Rule(emptySymbols));
        Rule newTokensRule = new Rule(insertTokens.stream().map((t) -> new Terminal(t, t)).collect(toList()));
        newNonTerminal.addRule(newTokensRule);

        // check if there are repeating patterns in the insertTokens
        List<GrammarMutatorUtils.RepeatingPattern> newValuesPatterns = GrammarMutatorUtils.computeShortestRepeatedPatterns(insertTokens, true);
        newValuesPatterns = newValuesPatterns.stream().filter(pattern -> pattern.getNrRepetitions() > ParameterSettings.MIN_NR_REPETITIONS).collect(toList());

        if (newValuesPatterns.size() > 0 && newValuesPatterns.get(0).getPattern().size() * newValuesPatterns.get(0).getNrRepetitions() >= insertTokens.size()) {
            // all new tokens are one repeating pattern
            for (GrammarMutatorUtils.RepeatingPattern newValuePattern : newValuesPatterns) {
                replaceRuleWithInfiniteRepetitions(newValuePattern, newTokensRule, 0);
            }
            modifyRule.insertSymbols(modifyPosition, Collections.singletonList(newTokensRule.getSymbols().get(0)));
        } else {
            modifyRule.insertSymbols(modifyPosition, Collections.singletonList(newNonTerminal));
        }
    }

    private void insertWithNewNonTerminalAndOrigRule(List<String> insertTokens, Rule modifyRule, int modifyPosition) {
        List<Symbol> a2Symbols = new ArrayList<>();
        a2Symbols.addAll(modifyRule.getSymbols().subList(modifyPosition, modifyRule.getSymbols().size()));
        modifyRule.deleteSymbols(modifyPosition, modifyRule.getSymbols().size());
        NonTerminal newNonTerminal = NonTerminalFactory.createNewNonTerminal(new Rule(a2Symbols));
        Rule newTokensRule = new Rule(insertTokens.stream().map((t) -> new Terminal(t, t)).collect(toList()));
        newNonTerminal.addRule(newTokensRule);

        // check if there are repeating patterns in the insertTokens
        List<GrammarMutatorUtils.RepeatingPattern> newValuesPatterns = GrammarMutatorUtils.computeShortestRepeatedPatterns(insertTokens, true);
        newValuesPatterns = newValuesPatterns.stream().filter(pattern -> pattern.getNrRepetitions() > ParameterSettings.MIN_NR_REPETITIONS).collect(toList());

        if (newValuesPatterns.size() > 0 && newValuesPatterns.get(0).getPattern().size() * newValuesPatterns.get(0).getNrRepetitions() >= insertTokens.size()) {
            // all new tokens are one repeating pattern
            for (GrammarMutatorUtils.RepeatingPattern newValuePattern : newValuesPatterns) {
                replaceRuleWithInfiniteRepetitions(newValuePattern, newTokensRule, 0);
            }
            modifyRule.insertSymbols(modifyPosition, Collections.singletonList(newTokensRule.getSymbols().get(0)));
        } else {
            modifyRule.insertSymbols(modifyPosition, Collections.singletonList(newNonTerminal));
        }

    }

    /**
     * Clean the rule stack from rules that only parsed empty symbols, i.e. they are irrelevant for finding the mutation position
     *
     * @param ruleListPositions
     * @param keepZeroPositionRules
     * @return a new {@link Stack<RuleParsingPosition> that contains only the relevant rules}
     */
    Stack<RuleParsingPosition> cleanRuleParsingStack2(List<RuleParsingPosition> ruleListPositions,
                                                      boolean keepZeroPositionRules) {

        Stack<RuleParsingPosition> returnStack = new Stack<>();

        int curCorrectionPostionOffset = 0;
        while (!ruleListPositions.isEmpty()) {
            RuleParsingPosition curRuleParsingPosition = ruleListPositions.get(ruleListPositions.size() - 1);
            ruleListPositions.remove(ruleListPositions.size() - 1);
            List<Symbol> parsedSymbols = new ArrayList<>();
            int nrOfNonTerminals = getNrNonTerminals(curRuleParsingPosition.getRule().getSymbols().subList(0, curRuleParsingPosition.getCurPosition()));

            while (nrOfNonTerminals == 0
                    && curRuleParsingPosition.getCurPosition() >= curRuleParsingPosition.getRule().getSymbols().size()) {
                parsedSymbols.addAll(curRuleParsingPosition.getRule().getSymbols().subList(0, curRuleParsingPosition.getCurPosition()));
                curRuleParsingPosition = ruleListPositions.get(ruleListPositions.size() - 1);
                ruleListPositions.remove(ruleListPositions.size() - 1);
                nrOfNonTerminals = getNrNonTerminals(curRuleParsingPosition.getRule().getSymbols().subList(0, curRuleParsingPosition.getCurPosition()));
            }

            if (curRuleParsingPosition.getCurPosition() < curRuleParsingPosition.getRule().getSymbols().size()) {

                parsedSymbols.addAll(curRuleParsingPosition.getRule().getSymbols().subList(0, curRuleParsingPosition.getCurPosition()));
                if (containsNonEmptySymbols(parsedSymbols) || keepZeroPositionRules && curRuleParsingPosition.getCurPosition() == 0) {

                    // correct parsingPosition if a nonTerminal with only empty rules was parsed
                    /*curRuleParsingPosition.setCurPosition(curRuleParsingPosition.getCurPosition() - curCorrectionPostionOffset);
                    if(curRuleParsingPosition.getCurPosition() < 0) {
                        curRuleParsingPosition.setCurPosition(curRuleParsingPosition.getCurPosition() + curCorrectionPostionOffset);
                    }*/

                    returnStack.push(curRuleParsingPosition);
                    curCorrectionPostionOffset = 0;
                } else if (parsedSymbols.size() > 0) {
                    curCorrectionPostionOffset++;
                }
            }
        }

        // revert returnStack
        Stack<RuleParsingPosition> reversedStack = new Stack<>();
        while (!returnStack.empty()) {
            reversedStack.push(returnStack.pop());
        }

        return reversedStack;

    }

    /**
     * Clean the rule stack from rules that only parsed empty symbols, i.e. they are irrelevant for finding the mutation position
     *
     * @param ruleListPositions
     * @param keepZeroPositionRules
     * @return a new {@link Stack<RuleParsingPosition> that contains only the relevant rules}
     */
    Stack<RuleParsingPosition> cleanRuleParsingStack (Stack<RuleParsingPosition> ruleStackPositions, List<RuleParsingPosition> ruleListPositions, boolean keepZeroPositionRules) {

        Stack<RuleParsingPosition> returnStack = new Stack<>();
        List<RuleParsingPosition> localRuleListPositions = new ArrayList<>(ruleListPositions);

        // copy the given ruleStack
        for (RuleParsingPosition ruleStackPosition : ruleStackPositions) {
            returnStack.push(new RuleParsingPosition(ruleStackPosition.getRule(), ruleStackPosition.getCurPosition()));
        }

        // iterate over stack elements from bottom to top and find last parsed symbols from list
        RuleParsingPosition curStackPosition;
        boolean nonEmptyInLastPosition = false;
        for (int i = returnStack.size() - 1; i >= 0 && !nonEmptyInLastPosition; i--) {
            curStackPosition = returnStack.get(i);

            if (curStackPosition.getCurPosition() > 0 && curStackPosition.getRule().getSymbols().get(curStackPosition.getCurPosition() - 1).isNonTerminal()) {
                // find curStackPosition on ruleLists
                int listIdx = localRuleListPositions.size() - 1;
                RuleParsingPosition curRuleListPosition = localRuleListPositions.get(listIdx);
                while (curRuleListPosition.getCurPosition() >= curRuleListPosition.getRule().getSymbols().size()) {
                    curRuleListPosition = localRuleListPositions.get(listIdx);
                    listIdx--;
                }
                assert curRuleListPosition.equals(curStackPosition);

                // reconstruct parsed symbols for every nonTerminal in curStackPositionRule
                int curListPositionIdx = localRuleListPositions.indexOf(curRuleListPosition);
                Stack<List<Symbol>> nonTerminalRulesLists = new Stack<>();
                for (int j = 0; j < curRuleListPosition.getCurPosition(); j++) {
                    if (curRuleListPosition.getRule().getSymbols().get(j).isNonTerminal()) {
                        nonTerminalRulesLists.push(reconstructTerminalSymbols(localRuleListPositions.subList(curListPositionIdx + 1, localRuleListPositions.size())));
                    }
                }

                // decrement curPosition as long as only empty symbols were parsed by a nonTerminal
                while (curStackPosition.getCurPosition() > 0 && curStackPosition.getRule().getSymbols().get(curStackPosition.getCurPosition() - 1).isNonTerminal() && !containsNonEmptySymbols(nonTerminalRulesLists.pop())) {
                    curStackPosition.setCurPosition(curStackPosition.getCurPosition() - 1);
                }

                nonEmptyInLastPosition = curStackPosition.getCurPosition() > 0;

                assert localRuleListPositions.get(localRuleListPositions.size() - 1).equals(curRuleListPosition);
                localRuleListPositions.remove(localRuleListPositions.size() - 1);
            } else {
                nonEmptyInLastPosition = true;
            }
        }


        if (!keepZeroPositionRules) {
            Stack<RuleParsingPosition> tmpStack = (Stack<RuleParsingPosition>) returnStack.clone();
            returnStack.clear();
            for (RuleParsingPosition tmpParsingPosition : tmpStack) {
                if (tmpParsingPosition.getCurPosition() > 0 || tmpStack.get(0).equals(tmpParsingPosition)) {
                    returnStack.push(tmpParsingPosition);
                }
            }
        }

        return returnStack;
    }

    /**
     * Recursivley reconstruct the terminalSymbols that were parsed by the first {@link RuleParsingPosition} in the given ruleListPositions
     *
     * @param ruleListPositions
     * @return
     */
    List<Symbol> reconstructTerminalSymbols(List<RuleParsingPosition> ruleListPositions) {

        RuleParsingPosition ruleParsingPosition = ruleListPositions.get(0);
        ruleListPositions.remove(0);

        if (!ruleParsingPosition.getRule().containsNonTerminals()) {
            return new ArrayList<>(ruleParsingPosition.getRule().getSymbols());
        } else {
            List<Symbol> terminalList = new ArrayList<>();
            for (Symbol symbol : ruleParsingPosition.getRule().getSymbols()) {
                if (symbol.isTerminal()) {
                    terminalList.add(symbol);
                } else {
                    terminalList.addAll(reconstructTerminalSymbols(ruleListPositions));
                }
            }
            return terminalList;
        }
    }


    private int getNrNonTerminals(List<Symbol> symbols) {
        return symbols.stream().filter(symbol -> symbol.isNonTerminal()).collect(toList()).size();
    }

    private boolean containsNonEmptySymbols(List<Symbol> symbols) {
        return symbols.stream().filter(symbol -> !symbol.getName().equals("") && symbol.isTerminal()).collect(toList()).size() > 0;
    }

}
