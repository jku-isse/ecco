package at.jku.isse.ecco.featuretrace;

import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.io.parsers.ParserException;


public class LogicUtils {

    public static Formula parseString(String string) {
        FormulaFactory formulaFactory = FormulaFactoryProvider.getFormulaFactory();
        try {
            return formulaFactory.parse(string);
        } catch (ParserException e){
            throw new RuntimeException("String-Representation of logic formula could not be parsed: " + string + ": " + e.getMessage());
        }
    }
}
