package at.jku.isse.ecco.featuretrace;

import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.io.parsers.ParserException;


public class LogicUtils {

    public static Formula parseString(FormulaFactory factory, String string) {
        try {
            return factory.parse(string);
        } catch (ParserException e){
            throw new RuntimeException("String-Representation of logic formula could not be parsed: " + string + ": " + e.getMessage());
        }
    }
}
