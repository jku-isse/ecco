package at.jku.isse.ecco.storage.ser.featuretrace;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import at.jku.isse.ecco.tree.Node;
import org.logicng.formulas.Formula;

import java.util.Objects;

import static at.jku.isse.ecco.logic.LogicUtils.parseString;

public class SerFeatureTrace implements FeatureTrace {
    private Node node;
    private String proactiveCondition;
    private String retroactiveCondition;

    public SerFeatureTrace(Node node){
        this.node = node;
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
            Formula currentFormula = parseString(currentCondition);
            Formula newFormula = parseString(newCondition);
            Formula combinedFormula = FormulaFactoryProvider.getFormulaFactory().or(currentFormula, newFormula);
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
            Formula currentCondition = parseString(this.proactiveCondition);
            Formula additionalCondition = parseString(proactiveCondition);
            Formula newCondition = FormulaFactoryProvider.getFormulaFactory().and(currentCondition, additionalCondition);
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
        if (!(featureTrace instanceof SerFeatureTrace)){
            throw new RuntimeException("Cannot fuse MemFeatureTrace with non-MemFeatureTrace.");
        }
        SerFeatureTrace serFeatureTrace = (SerFeatureTrace) featureTrace;
        this.addRetroactiveCondition(serFeatureTrace.retroactiveCondition);
        this.addProactiveCondition(serFeatureTrace.proactiveCondition);
    }

    @Override
    public String getOverallConditionString(EvaluationStrategy evaluationStrategy) {
        if (this.retroactiveCondition == null && this.proactiveCondition == null){
            throw new RuntimeException("Neither retroactive nor proactive condition exists.");
        } else  {
            return evaluationStrategy.getOverallConditionString(this.proactiveCondition, this.retroactiveCondition);
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
        if (!(o instanceof SerFeatureTrace)) return false;
        SerFeatureTrace serFeatureTrace = (SerFeatureTrace) o;

        if (this.node == null){
            if (serFeatureTrace.node != null) { return false; }
        } else if (!this.node.equals(serFeatureTrace.node)) { return false; }

        if (this.proactiveCondition == null){
            if (serFeatureTrace.proactiveCondition != null) { return false; }
        } else if (!(this.proactiveCondition.equals(serFeatureTrace.proactiveCondition))) { return false; }

        if (this.retroactiveCondition == null){
            if (serFeatureTrace.retroactiveCondition != null) { return false; }
        } else if (!(this.retroactiveCondition.equals(serFeatureTrace.retroactiveCondition))) { return false; }

        return true;
    }

    @Override
    public int hashCode(){
        return Objects.hash(this.node, this.proactiveCondition, this.retroactiveCondition);
    }
}
