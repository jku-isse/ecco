package at.jku.isse.ecco.experiment.result;

import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.FormulaFactory;
import org.logicng.formulas.Literal;

import java.util.Collection;
import java.util.LinkedList;


public class AssignmentPowerset {

    public static Collection<Assignment> getAssignmentPowerset(Collection<String> literalStrings){
        FormulaFactory formulaFactory = FormulaFactoryProvider.getFormulaFactory();
        Collection<Literal> literals = literalStrings.stream().
                map(s -> formulaFactory.literal(s, true))
                .toList();
        Collection<Assignment> assignments = new LinkedList<>();
        for (Literal literal : literals){
            assignments = doubleWithAddedLiteral(assignments, literal);
        }
        for (Assignment assignment : assignments){
            assignment.addLiteral(formulaFactory.literal("BASE", true));
        }
        return assignments;
    }

    private static Collection<Assignment> doubleWithAddedLiteral(Collection<Assignment> assignments, Literal literal){
        Collection<Assignment> newAssignments = new LinkedList<>(assignments);

        if (assignments.size() == 0){
            newAssignments.add(new Assignment());
            newAssignments.add(new Assignment(literal));
            return newAssignments;
        }

        for (Assignment assignment : assignments){
            Assignment newAssignment = new Assignment(assignment.literals());
            newAssignment.addLiteral(literal);
            newAssignments.add(newAssignment);
        }
        return newAssignments;
    }
}
