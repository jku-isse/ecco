package at.jku.isse.ecco.experiment.mistake;

import at.jku.isse.ecco.experiment.utils.CollectionUtils;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.LogicUtils;
import at.jku.isse.ecco.repository.Repository;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.formulas.Variable;

import java.util.SortedSet;


public class Unconjugator implements MistakeStrategy {

    private final FormulaFactory formulaFactory = new FormulaFactory();

    @Override
    public void createMistake(FeatureTrace trace) {
        try {
            String userCondition = trace.getUserConditionString();
            Formula conditionFormula = LogicUtils.parseString(this.formulaFactory, userCondition);
            Formula cnf = conditionFormula.cnf();
            SortedSet<Variable> variables = cnf.variables();
            if (variables.size() < 2) {
                // there is no conjunction in the formula
                throw new RuntimeException("too little variables in featuretrace.");
            }
            // for simplicity reasons, only consider formulas without disjunctions
            if (userCondition.indexOf('|') != -1){
                throw new RuntimeException("only consider formulas without disjunction.");
            }
            // for simplicity reasons, only consider formulas without negation
            if (userCondition.indexOf('~') != -1){
                throw new RuntimeException("only consider formulas without negation.");
            }
            Variable toBeSwitched = CollectionUtils.getRandom(variables);
            String cnfString = cnf.toString();
            String newCondition = cnfString.replace(toBeSwitched.name(), formulaFactory.verum().toString());
            trace.setUserCondition(newCondition);
        } catch (Exception e){
            throw new RuntimeException("Unconjugator failed to create mistake.");
        }
    }

    @Override
    public void init(Repository.Op repository) {}

    @Override
    public String toString(){
        return "Unconjugator";
    }
}
