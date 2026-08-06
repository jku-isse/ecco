package at.jku.isse.ecco.genericAdapter.grammarInferencer.mutate;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Symbol;

import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * Created by Michi on 14.09.2015.
 */
public class GrammarMutatorUtils {

    /**
     *
     * @param rootSymbol
     * @param nonTerminal
     * @return all positions in the given grammar (i.e. the rootSymbol) where the given nonTerminal is used
     */
    static List<RuleParsingPosition> findNonTerminalOccurencesInGrammar(NonTerminal rootSymbol, NonTerminal nonTerminal) {
        List<RuleParsingPosition> result = new ArrayList<>();

        Queue<NonTerminal> nonTerminalMap = new LinkedList<>();
        Set<String> processedNonTerminals = new HashSet<>();

        for (Rule rule : rootSymbol.getRules()) {
            for (Symbol symbol : rule.getSymbols()) {
                if(symbol.isNonTerminal()) {
                    if(symbol.getName().equals(nonTerminal.getName())) {
                        result.add(new RuleParsingPosition(rule, rule.getSymbols().indexOf(symbol)));
                    } else {
                        nonTerminalMap.add((NonTerminal) symbol);
                    }
                }
            }
        }

//        while(!nonTerminalMap.isEmpty()) {
            NonTerminal curNonTerminal;
            while(!nonTerminalMap.isEmpty()) {
                curNonTerminal = nonTerminalMap.remove();
                for (Rule rule : curNonTerminal.getRules()) {
                    for (Symbol symbol : rule.getSymbols()) {
                        if (symbol.isNonTerminal()) {
                            if (symbol.getName().equals(nonTerminal.getName())) {
                                result.add(new RuleParsingPosition(rule, rule.getSymbols().indexOf(symbol)));
                            } else if(!processedNonTerminals.contains(symbol.getName())){
                                nonTerminalMap.add((NonTerminal) symbol);
                            }
                        }
                    }
                }

                processedNonTerminals.add(curNonTerminal.getName());
            }
//        }

        return result;
    }

    /**
     * @param inputList
     * @param includeSimilarPattern
     * @return a list of {@link RepeatingPattern} that contains the susequent repeated patterns contained in the input List
     */
    public static List<RepeatingPattern> computeShortestRepeatedPatterns(List<String> inputList, boolean includeSimilarPattern) {
        RepeatingPattern[] repeatingPatternsList = new RepeatingPattern[inputList.size()];

        for (int curTokenIdx = 0; curTokenIdx < inputList.size(); curTokenIdx++) {

            String curToken = inputList.get(curTokenIdx);

            for (int i = curTokenIdx + 1; i < inputList.size(); i++) {
                if (curToken.equals(inputList.get(i))) {
                    // match found, check next characters
                    int curTokenOffset = i - curTokenIdx - 1;
                    while (curTokenOffset > 0 && i + curTokenOffset < inputList.size()
                            && inputList.get(curTokenIdx + curTokenOffset).equals(inputList.get(i + curTokenOffset))) {
                        curTokenOffset--;
                    }
                    if (curTokenOffset == 0) {
                        if(repeatingPatternsList[curTokenIdx] == null) {
                            List<String> pattern = new ArrayList<>();

                            // match found for string from curCharIdx to i -1
                            for (int j = curTokenIdx; j < i; j++) {
                                pattern.add(inputList.get(j));
                            }
                            // find out how often the found pattern occurs subsequently
                            int nrRepetions = 0;
                            int curRepetionsIdx = 0;
                            int curInputListIdx = curTokenIdx;
                            while (inputList.size() > curInputListIdx && pattern.get(curRepetionsIdx).equals(inputList.get(curInputListIdx))) {
                                curRepetionsIdx++;
                                curInputListIdx++;

                                if (curRepetionsIdx >= pattern.size()) {
                                    curRepetionsIdx = 0;
                                    nrRepetions++;
                                }
                            }
                            repeatingPatternsList[curTokenIdx] = new RepeatingPattern(pattern, curTokenIdx, nrRepetions);
                        }
                    }
                }
            }
        }


        // filter results
        List<RepeatingPattern> filterPatterns = new ArrayList<>();


        for (int i = 0; i < repeatingPatternsList.length; i++) {
            if(repeatingPatternsList[i] != null) {

                // filter patterns that are already included in other repetions
                int curRepetion = 0;
                for (int j = i + repeatingPatternsList[i].getPattern().size(); j < repeatingPatternsList.length && curRepetion < repeatingPatternsList[i].getNrRepetitions(); j+=repeatingPatternsList[i].getPattern().size()) {
                    curRepetion++;

                    if(repeatingPatternsList[j] != null && repeatingPatternsList[i].getPattern().equals(repeatingPatternsList[j].getPattern())) {
                        repeatingPatternsList[j] = null;
                    }
                }

                if(!includeSimilarPattern) {
                    // filter patterns that are just variations of previous patterns (e.g. REF, and ,REF)
                    for (int j = i + 1; j < i + (repeatingPatternsList[i].getPattern().size() * repeatingPatternsList[i].getNrRepetitions()); j++) {
                        if (repeatingPatternsList[j] != null && repeatingPatternsList[i].getPattern().size() == repeatingPatternsList[j].getPattern().size()) {
                            repeatingPatternsList[j] = null;
                        }
                    }
                }
            }
        }

        filterPatterns = Arrays.asList(repeatingPatternsList).stream().filter(pattern -> pattern != null).collect(toList());

        return filterPatterns;
    }

    public static class RepeatingPattern {
        private List<String> pattern;
        private int startIdx;
        private int nrRepetitions;


        private RepeatingPattern(List<String> pattern, int startIdx, int nrRepetitions) {
            this.pattern = pattern;
            this.startIdx = startIdx;
            this.nrRepetitions = nrRepetitions;
        }

        public int getNrRepetitions() {
            return nrRepetitions;
        }

        public int getStartIdx() {
            return startIdx;
        }

        public List<String> getPattern() {
            return pattern;
        }

        public void setStartIdx(int startIdx) {
            this.startIdx = startIdx;
        }
    }
}
