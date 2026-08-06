package at.jku.isse.ecco.genericAdapter.grammarInferencer.facade;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal;
import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Michael Jahn
 */
public class GrammarOptimizationService {


    NonTerminal optimizeGrammar(NonTerminal rootSymbol) {

        Set<NonTerminal> nonTerminalSet = rootSymbol.getAllNonTerminalsRecursive();

        // remove duplicate nonTerminals, i.e. ones with the exact same rules
        Map<Integer, NonTerminal> ruleHashCodes = new HashMap<>();
        Map<String, NonTerminal> nonTerminalsReplaceMap = new HashMap<>();
        for (NonTerminal nonTerminal : nonTerminalSet) {
            int ruleHash = nonTerminal.getRules().hashCode();
            if(ruleHashCodes.containsKey(ruleHash)) {
                if(ParameterSettings.DEBUG_OUTPUT) {
                    System.out.println("Found duplicate nonTerminals: \n" +
                            ruleHashCodes.get(ruleHash).toString() + "\n" +
                            nonTerminal.toString());
                }
                nonTerminalsReplaceMap.put(nonTerminal.getName(), ruleHashCodes.get(ruleHash));
            } else {
                ruleHashCodes.put(ruleHash, nonTerminal);
            }
        }
        for (NonTerminal nonTerminal : nonTerminalSet) {
            for (Rule rule : nonTerminal.getRules()) {
                for (int i = 0; i < rule.getSymbols().size(); i++) {
                    if(nonTerminalsReplaceMap.containsKey(rule.getSymbols().get(i).getName())) {
                        rule.replaceSymbol(i, nonTerminalsReplaceMap.get(rule.getSymbols().get(i).getName()));
                    }
                }
            }
        }

        return rootSymbol;
    }
}
