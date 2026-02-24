package at.jku.isse.ecco.logic;

import org.logicng.formulas.FormulaFactory;

/**
 * A formula factory must not be accessed from multiple threads at the same time.
 * (see <a href="https://logicng.org/documentation/formula-factory/">Formula Factory</a>)
 * This class provides a thread-local instance that can be used in a multithreaded environment.
 */
public class FormulaFactoryProvider {

    private static final ThreadLocal<FormulaFactory> formulaFactoryThreadLocal = ThreadLocal.withInitial(FormulaFactory::new);

    public static FormulaFactory getFormulaFactory(){
        return formulaFactoryThreadLocal.get();
    }
}
