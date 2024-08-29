package at.jku.isse.ecco.experiment.result;

import org.logicng.datastructures.Assignment;
import org.logicng.formulas.FormulaFactory;
import org.logicng.formulas.Literal;

import java.util.Collection;
import java.util.LinkedList;


public class AssignmentPowerset {

    public static Collection<Assignment> getAssignmentPowerset(FormulaFactory factory, Collection<String> literalStrings){
        Collection<Literal> literals = literalStrings.stream().
                map(s -> factory.literal(s, true))
                .toList();
        Collection<Assignment> assignments = new LinkedList<>();
        for (Literal literal : literals){
            assignments = doubleWithAddedLiteral(assignments, literal);
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
