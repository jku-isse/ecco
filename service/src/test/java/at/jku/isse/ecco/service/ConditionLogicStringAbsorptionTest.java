package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import at.jku.isse.ecco.logic.LogicUtils;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.ModuleRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.logicng.datastructures.Tristate;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.solvers.MiniSat;
import org.logicng.solvers.SATSolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Condition.toLogicString() - every node's retroactive condition, evaluated at checkout - leaves
 * out module revisions absorbed by one with a subset of their literals. It must stay equivalent to
 * the full disjunction it used to write ({@link #oldToLogicString}, the previous implementation),
 * for every association of a repository whose conditions contain a module lattice, several
 * revisions of a feature and features that are absent in some commits.
 */
public class ConditionLogicStringAbsorptionTest {

    /** The implementation before absorption, verbatim. */
    private static String oldToLogicString(Condition condition) {
        FormulaFactory formulaFactory = FormulaFactoryProvider.getFormulaFactory();
        Collection<Formula> moduleFormulas = new LinkedList<>();
        for (Collection<ModuleRevision> moduleRevisions : condition.getModules().values()) {
            Collection<Formula> moduleRevisionFormulas = moduleRevisions.stream()
                    .map(ModuleRevision::getConditionString)
                    .map(LogicUtils::parseString)
                    .collect(Collectors.toList());
            moduleFormulas.add(formulaFactory.or(moduleRevisionFormulas));
        }
        return formulaFactory.or(moduleFormulas).toString();
    }

    @Test
    @Timeout(120)
    public void absorbedConditionIsEquivalentToTheFullDisjunction() throws Exception {
        Path workDir = Files.createTempDirectory("condition-absorption");
        Path content = Files.createDirectories(workDir.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            for (int i = 0; i < 6; i++)
                Files.writeString(content.resolve("base" + i + ".txt"), "base " + i + "\n");

            String[] configurations = {"A", "A, B", "B, C", "A.2, C", "A.2, B, D", "C, D, E", "A, E", "B.2, D"};
            for (int c = 0; c < configurations.length; c++) {
                for (int i = 0; i < 6; i++)
                    if ((i + c) % 3 == 0)
                        Files.writeString(content.resolve("base" + i + ".txt"), "commit " + c + "\n", java.nio.file.StandardOpenOption.APPEND);
                Files.writeString(content.resolve("only" + c + ".txt"), "only " + c + "\n");
                service.commit("c" + c, configurations[c]);
            }

            FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
            int oldTerms = 0, newTerms = 0;
            for (Association association : service.getRepository().getAssociations()) {
                Condition condition = association.computeCondition();
                String oldString = oldToLogicString(condition);
                String newString = condition.toLogicString();

                Formula oldFormula = LogicUtils.parseString(oldString);
                Formula newFormula = LogicUtils.parseString(newString);
                SATSolver solver = MiniSat.miniSat(f);
                solver.add(f.not(f.equivalence(oldFormula, newFormula)));
                assertEquals(Tristate.FALSE, solver.sat(), () -> "not equivalent for " + association.getId() + ":\nold " + oldString + "\nnew " + newString);

                assertEquals(newString, association.computeCondition().toLogicString(), "deterministic");
                oldTerms += condition.getModules().values().stream().mapToInt(Collection::size).sum();
                newTerms += newString.split("\\|").length;
            }
            assertTrue(newTerms < oldTerms, "absorption removed nothing: " + newTerms + " of " + oldTerms);
        }
    }
}
