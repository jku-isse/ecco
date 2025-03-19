package at.jku.isse.ecco.storage.mem.featuretrace;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.tree.Node;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.io.parsers.ParserException;

import java.util.Objects;

public class MemFeatureTrace implements FeatureTrace {
    private Node node;
    private String proactiveCondition;
    private String retroactiveCondition;
    private final transient FormulaFactory formulaFactory;


    public MemFeatureTrace(Node node){
        this.node = node;
        this.formulaFactory = new FormulaFactory();
    }

    @Override
    public boolean holds(Configuration configuration, EvaluationStrategy evaluationStrategy){
        return evaluationStrategy.holds(configuration, this.proactiveCondition, this.retroactiveCondition);
    }

    @Override
    public Node getNode() {
        return this.node;
    }

    @Override
    public void setNode(Node node) {
        this.node = node;
    }

    @Override
    public boolean containsProactiveCondition() {
        return (this.proactiveCondition != null);
    }

    @Override
    public void addProactiveCondition(String proactiveCondition){
        if (proactiveCondition == null){ return; }
        this.proactiveCondition = this.combineConditions(this.proactiveCondition, proactiveCondition);
    }

    @Override
    public void removeProactiveCondition(){
        this.proactiveCondition = null;
    }

    @Override
    public void addRetroactiveCondition(String retroactiveCondition){
        this.retroactiveCondition = this.combineConditions(this.retroactiveCondition, retroactiveCondition);
    }

    private String combineConditions(String currentCondition, String newCondition){
        if (newCondition == null) { return currentCondition; }
        newCondition = this.sanitizeFormulaString(newCondition);
        if (currentCondition == null){
            return newCondition;
        } else {
            Formula currentFormula = this.parseString(currentCondition);
            Formula newFormula = this.parseString(newCondition);
            Formula combinedFormula = this.formulaFactory.or(currentFormula, newFormula);
            return combinedFormula.toString();
        }
    }

    @Override
    public void buildProactiveConditionConjunction(String proactiveCondition) {
        if (proactiveCondition == null) { return; }
        proactiveCondition = this.sanitizeFormulaString(proactiveCondition);
        if (this.proactiveCondition == null){
            this.proactiveCondition = proactiveCondition;
        } else {
            Formula currentCondition = this.parseString(this.proactiveCondition);
            Formula additionalCondition = this.parseString(proactiveCondition);
            Formula newCondition = this.formulaFactory.and(currentCondition, additionalCondition);
            this.proactiveCondition = newCondition.toString();
        }
    }

    @Override
    public String getProactiveConditionString() {
        return this.proactiveCondition;
    }

    @Override
    public String getRetroactiveConditionString() {
        return this.retroactiveCondition;
    }

    @Override
    public void fuseFeatureTrace(FeatureTrace featureTrace) {
        if (!(featureTrace instanceof MemFeatureTrace)){
            throw new RuntimeException("Cannot fuse MemFeatureTrace with non-MemFeatureTrace.");
        }
        MemFeatureTrace memFeatureTrace = (MemFeatureTrace) featureTrace;
        this.addRetroactiveCondition(memFeatureTrace.retroactiveCondition);
        this.addProactiveCondition(memFeatureTrace.proactiveCondition);
    }

    @Override
    public String getOverallConditionString(EvaluationStrategy evaluationStrategy) {
        if (this.retroactiveCondition == null && this.proactiveCondition == null){
            throw new RuntimeException("Neither retroactive nor proactive condition exists.");
        } else  {
            return evaluationStrategy.getOverallConditionString(this.proactiveCondition, this.retroactiveCondition);
        }
    }

    private Formula parseString(String string) {
        try {
            return this.formulaFactory.parse(string);
        } catch (ParserException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setRetroactiveCondition(String retroactiveConditionString) {
        this.retroactiveCondition = retroactiveConditionString;
    }

    @Override
    public void setProactiveCondition(String proactiveConditionString) {
        proactiveConditionString = this.sanitizeFormulaString(proactiveConditionString);
        this.proactiveCondition = proactiveConditionString;
    }

    private String sanitizeFormulaString(String formulaString){
        // "." and "-" cannot be parsed by FormulaFactory
        // conditions replace "." with "_" for indication of feature-revision-id
        // as opposed to documentation, "#" and "@" do not parse, which is why only "_" is used
        // conditions replace "-" with "_" for UUIDs (Feature-revision-IDs)
        if (formulaString == null) { return null; }
        formulaString = formulaString.replace(".", "_");
        formulaString = formulaString.replace("-", "_");
        return formulaString;
    }

    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof MemFeatureTrace)) return false;
        MemFeatureTrace memFeatureTrace = (MemFeatureTrace) o;

        if (this.node == null){
            if (memFeatureTrace.node != null) { return false; }
        } else if (!this.node.equals(memFeatureTrace.node)) { return false; }

        if (this.proactiveCondition == null){
            if (memFeatureTrace.proactiveCondition != null) { return false; }
        } else if (!(this.proactiveCondition.equals(memFeatureTrace.proactiveCondition))) { return false; }

        if (this.retroactiveCondition == null){
            if (memFeatureTrace.retroactiveCondition != null) { return false; }
        } else if (!(this.retroactiveCondition.equals(memFeatureTrace.retroactiveCondition))) { return false; }

        return true;
    }

    @Override
    public int hashCode(){
        return Objects.hash(this.node, this.proactiveCondition, this.retroactiveCondition);
    }
}
