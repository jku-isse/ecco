package mistake;

import at.jku.isse.ecco.experiment.result.AssignmentPowerset;
import at.jku.isse.ecco.experiment.result.Result;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.junit.jupiter.api.Test;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.io.parsers.ParserException;

import java.util.ArrayList;
import java.util.Collection;

public class ConjugatorEvaluationTest {


    @Test
    public void sampleTest() throws ParserException {
        FormulaFactory formulaFactory = FormulaFactoryProvider.getFormulaFactory();

        Formula groundTruth = formulaFactory.parse("~DEFINED___LB___WIN32__RB__ & ~DEFINED___LB__TARGET_AIX__RB__ & ~DEFINED___LB__TARGET_OPENBSD__RB__ & ~DEFINED___LB__TARGET_NETBSD__RB__ & ~DEFINED___LB__TARGET_DARWIN__RB__ & ~DEFINED___LB__TARGET_FREEBSD__RB__ & ~DEFINED___LB__TARGET_DRAGONFLY__RB__ & ~DEFINED___LB__TARGET_SOLARIS__RB__ & ~DEFINED___LB__TARGET_ANDROID__RB__ & ~DEFINED___LB__TARGET_LINUX__RB__ | DEFINED___LB__TARGET_DRAGONFLY__RB__ & ~DEFINED___LB__TARGET_FREEBSD__RB__ & ~DEFINED___LB__TARGET_NETBSD__RB__ & ~DEFINED___LB__TARGET_OPENBSD__RB__ & ~DEFINED___LB__TARGET_SOLARIS__RB__ & ~DEFINED___LB__TARGET_ANDROID__RB__ & ~DEFINED___LB__TARGET_LINUX__RB__");
        Formula formula = formulaFactory.parse("(~DEFINED___LB___WIN32__RB__ & ~DEFINED___LB__TARGET_AIX__RB__ & ~DEFINED___LB__TARGET_OPENBSD__RB__ & ~DEFINED___LB__TARGET_NETBSD__RB__ & ~DEFINED___LB__TARGET_DARWIN__RB__ & ~DEFINED___LB__TARGET_FREEBSD__RB__ & ~DEFINED___LB__TARGET_DRAGONFLY__RB__ & ~DEFINED___LB__TARGET_SOLARIS__RB__ & ~DEFINED___LB__TARGET_ANDROID__RB__ & ~DEFINED___LB__TARGET_LINUX__RB__ | DEFINED___LB__TARGET_DRAGONFLY__RB__ & ~DEFINED___LB__TARGET_FREEBSD__RB__ & ~DEFINED___LB__TARGET_NETBSD__RB__ & ~DEFINED___LB__TARGET_OPENBSD__RB__ & ~DEFINED___LB__TARGET_SOLARIS__RB__ & ~DEFINED___LB__TARGET_ANDROID__RB__ & ~DEFINED___LB__TARGET_LINUX__RB__) & MTU_H");

        //~DEFINED___LB___WIN32__RB__ & ~DEFINED___LB__TARGET_AIX__RB__ & ~DEFINED___LB__TARGET_OPENBSD__RB__ & ~DEFINED___LB__TARGET_NETBSD__RB__ & ~DEFINED___LB__TARGET_DARWIN__RB__ & ~DEFINED___LB__TARGET_FREEBSD__RB__ & ~DEFINED___LB__TARGET_DRAGONFLY__RB__ & ~DEFINED___LB__TARGET_SOLARIS__RB__ & ~DEFINED___LB__TARGET_ANDROID__RB__ & ~DEFINED___LB__TARGET_LINUX__RB__

        this.analyseFormulaPair(formula, groundTruth);
    }


    @Test
    public void correctNodesAreEvaluated() throws ParserException {
        FormulaFactory formulaFactory = FormulaFactoryProvider.getFormulaFactory();

        analyseFormulaPair(formulaFactory.parse("A & C"), formulaFactory.parse("A"));
        System.out.println();
        analyseFormulaPair(formulaFactory.parse("A & B & C"), formulaFactory.parse("A & B"));
        System.out.println();
        analyseFormulaPair(formulaFactory.parse("(A | B) & C"), formulaFactory.parse("A | B"));
        System.out.println();
        analyseFormulaPair(formulaFactory.parse("~A & C"), formulaFactory.parse("~A"));
    }

    private void analyseFormulaPair(Formula formula, Formula groundTruth){;
        Collection<String> features = new ArrayList<>();
        features.add("ENABLE_LZO");
        features.add("HAVE_SYS_UN_H");
        features.add("MTU_H");
        features.add("HAVE_NICE");
        features.add("DEFINED___LB__SO_SNDBUF__RB__");
        features.add("DEFINED___LB__HAVE_DUP__RB__");
        features.add("DEFINED___LB__TARGET_DRAGONFLY__RB__");
        features.add("HELPER_H");
        features.add("MBEDTLS_VERSION_NUMBER__GT__0x03000000");
        features.add("DEFINED___LB__HAVE_FTRUNCATE__RB__");

        Collection<Assignment> assignments = AssignmentPowerset.getAssignmentPowerset(features);
        Result result = new Result();

        result.updateResult(formula, groundTruth, assignments);
        result.computeMetrics();

        ConditionSwapAnalysis analysis = new ConditionSwapAnalysis(formula, groundTruth, result);
        analysis.printAnalysis();
    }
}
