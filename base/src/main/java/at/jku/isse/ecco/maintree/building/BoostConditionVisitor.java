package at.jku.isse.ecco.maintree.building;

import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import at.jku.isse.ecco.logic.LogicUtils;
import at.jku.isse.ecco.tree.Node;
import org.logicng.datastructures.Tristate;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.solvers.MiniSat;
import org.logicng.solvers.SATSolver;

public class BoostConditionVisitor implements Node.Op.NodeVisitor {

    // there must be a user condition and no other contradicting user condition in the association
    private boolean boostPossible = false;
    private boolean contradicted = false;
    String conditionCandidate;

    // todo: create a visitor pattern that may stop visiting at some point
    @Override
    public void visit(Node.Op node) {
        if (this.contradicted) {
            return;
        }
        FeatureTrace featureTrace = node.getFeatureTrace();
        if (featureTrace == null){
            return;
        }
        String userCondition = featureTrace.getProactiveConditionString();
        if (userCondition == null){
            return;
        } else if (this.conditionCandidate == null) {
            this.boostPossible = true;
            this.conditionCandidate = userCondition;
        } else if (!this.conditionCandidate.equals(userCondition) && !equivalent(this.conditionCandidate, userCondition)) {
            // the same condition written differently ("A & B", "B & A") is no contradiction
            this.boostPossible = false;
            this.contradicted = true;
        }
    }

    private static boolean equivalent(String a, String b) {
        FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
        Formula formulaA = LogicUtils.parseString(a);
        Formula formulaB = LogicUtils.parseString(b);
        SATSolver solver = MiniSat.miniSat(f);
        solver.add(f.not(f.equivalence(formulaA, formulaB)));
        return solver.sat() == Tristate.FALSE;
    }

    public boolean isBoostPossible(){
        return this.boostPossible;
    }

    public String getBoostCondition(){
        if (!this.boostPossible){
            throw new RuntimeException("Boost condition may not be fetched if boost is not possible.");
        }
        return this.conditionCandidate;
    }
}
