package at.jku.isse.ecco.logic;

import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.io.parsers.ParserException;
import org.logicng.io.parsers.PropositionalParser;


public class LogicUtils {

    public static Formula parseString(String string) {
        // string must conform to grammar
        // https://github.com/logic-ng/parser/blob/main/src/main/antlr/LogicNGPropositional.g4
        try {
            FormulaFactory formulaFactory = FormulaFactoryProvider.getFormulaFactory();
            PropositionalParser parser = new PropositionalParser(formulaFactory);
            return parser.parse(string);
        } catch (ParserException e){
            throw new LogicException("String could not be parsed according to grammar " +
                    "https://github.com/logic-ng/parser/blob/main/src/main/antlr/LogicNGPropositional.g4: " +
                    e.getMessage());
        }
    }
}
