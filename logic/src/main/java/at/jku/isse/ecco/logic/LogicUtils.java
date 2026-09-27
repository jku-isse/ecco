package at.jku.isse.ecco.logic;

import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.io.parsers.ParserException;
import org.logicng.io.parsers.PropositionalParser;

import java.util.LinkedHashMap;
import java.util.Map;


public class LogicUtils {

    /**
     * Parsed formulas by source string, per thread (formulas belong to the thread's
     * FormulaFactoryProvider factory, which keeps them anyway, so this adds little memory). Checkout
     * evaluates every node's condition, and all nodes of an association carry the same condition
     * string - which grows quickly with the number of commits (hundreds of KB after 15 commits) - so
     * parsing it once per node made checkout time grow steeply: 15 commits ~13s, 40 commits ~5 min.
     */
    private static final int PARSE_CACHE_SIZE = 1024;
    private static final ThreadLocal<Map<String, Formula>> parseCache = ThreadLocal.withInitial(() ->
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Formula> eldest) {
                    return this.size() > PARSE_CACHE_SIZE;
                }
            });

    public static Formula parseString(String string) {
        // string must conform to grammar
        // https://github.com/logic-ng/parser/blob/main/src/main/antlr/LogicNGPropositional.g4
        Map<String, Formula> cache = parseCache.get();
        Formula cached = cache.get(string);
        if (cached != null)
            return cached;
        try {
            FormulaFactory formulaFactory = FormulaFactoryProvider.getFormulaFactory();
            PropositionalParser parser = new PropositionalParser(formulaFactory);
            Formula formula = parser.parse(string);
            cache.put(string, formula);
            return formula;
        } catch (ParserException e){
            throw new LogicException("String could not be parsed according to grammar " +
                    "https://github.com/logic-ng/parser/blob/main/src/main/antlr/LogicNGPropositional.g4: " +
                    e.getMessage(), e);
        }
    }
}
