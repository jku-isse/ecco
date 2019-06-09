package at.jku.isse.ecco.genericAdapter.grammarInferencer.ruleInductionSequitur;

import at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminalFactory;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

/**
 * @author Michael Jahn
 */
public class SequiturRuleConverter {

    private static at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule convertRule(Rule sequiturRule) {
        at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule rule = new at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule(new ArrayList<>());
        at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Symbol curSymbol;

        for (Symbol sequiturSym = sequiturRule.first(); (!sequiturSym.isGuard()); sequiturSym = sequiturSym.n) {

            if (sequiturSym.isNonTerminal()) {
                String name = sequiturSym.value;

                int nonTermNr = Integer.parseInt(StringUtils.substringAfter(name, Symbol.NON_TERMINAL_CONST));

                curSymbol = NonTerminalFactory.getNonTerminalWithName(at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal.NON_TERMINAL_NAME_PREFIX + nonTermNr, convertRule(((NonTerminal) sequiturSym).r));
            } else {

                curSymbol = new at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Terminal(sequiturSym.value,sequiturSym.value);
            }
            rule.appendSymbol(curSymbol);
        }
        return rule;
    }

    public static at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Symbol convertToInternalDataStructure(Rule sequiturRule) {

        at.jku.isse.ecco.genericAdapter.grammarInferencer.data.Rule rootRule = convertRule((sequiturRule));
        at.jku.isse.ecco.genericAdapter.grammarInferencer.data.NonTerminal rootSymbol = NonTerminalFactory.getNonTerminalWithName("root", rootRule);

        return rootSymbol;
    }
}
