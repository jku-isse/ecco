package at.jku.isse.ecco.logic;

import org.junit.jupiter.api.Test;
import org.logicng.formulas.Formula;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * parseString() caches parsed formulas per thread (see its javadoc): a repeated string must not be
 * parsed again, and a formula must never cross into another thread's FormulaFactory.
 */
public class LogicUtilsParseCacheTest {

    @Test
    public void aRepeatedStringIsParsedOnceAndFormulasStayWithTheirThreadsFactory() throws Exception {
        String condition = "(A & B) | (~C & D)";
        Formula first = LogicUtils.parseString(condition);
        assertSame(first, LogicUtils.parseString(condition));
        assertSame(FormulaFactoryProvider.getFormulaFactory(), first.factory());

        Formula other = CompletableFuture.supplyAsync(() -> LogicUtils.parseString(condition)).get();
        assertNotSame(first, other);
        assertNotSame(first.factory(), other.factory());
    }
}
