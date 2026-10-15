package at.jku.isse.ecco.storage.ser.featuretrace;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureLiterals;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.tree.Node;

import java.util.Objects;

public class SerFeatureTrace implements FeatureTrace {

    private static final long serialVersionUID = 1621713735051207581L;
    private Node node;
    // the proactive condition's text, as stored
    private String proactiveCondition;
    // ... and as a condition (derived from the text, made when first asked for)
    private transient LogicCondition proactive;
    // derived from the association's counters (Association.Op#setRetroactiveConditions, after every
    // commit and merge and when a repository is read) - not stored: stored, it went stale whenever a
    // commit changed the condition of an association it did not write (ReopenEquivalenceTest), it
    // repeated the condition on every unique node, and a changed condition rewrote the association.
    private transient LogicCondition retroactiveCondition;

    public SerFeatureTrace(Node node){
        this.node = node;
    }

    @Override
    public boolean holds(Configuration configuration, EvaluationStrategy evaluationStrategy){
        return evaluationStrategy.holds(configuration, this.getProactiveCondition(), this.retroactiveCondition);
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
    public void addProactiveCondition(LogicCondition proactiveCondition){
        if (proactiveCondition == null){ return; }
        this.setProactiveCondition(LogicCondition.or(this.getProactiveCondition(), sanitized(proactiveCondition)));
    }

    @Override
    public void removeProactiveCondition(){
        this.setProactiveCondition((LogicCondition) null);
    }

    @Override
    public void addRetroactiveCondition(LogicCondition retroactiveCondition){
        this.retroactiveCondition = LogicCondition.or(this.retroactiveCondition, sanitized(retroactiveCondition));
    }

    @Override
    public void buildProactiveConditionConjunction(String proactiveCondition) {
        if (proactiveCondition == null) { return; }
        this.setProactiveCondition(LogicCondition.and(this.getProactiveCondition(), LogicCondition.of(FeatureLiterals.sanitize(proactiveCondition))));
    }

    @Override
    public LogicCondition getProactiveCondition() {
        if (this.proactive == null && this.proactiveCondition != null)
            this.proactive = LogicCondition.of(this.proactiveCondition);
        return this.proactive;
    }

    @Override
    public LogicCondition getRetroactiveCondition() {
        return this.retroactiveCondition;
    }

    @Override
    public void fuseFeatureTrace(FeatureTrace featureTrace) {
        if (!(featureTrace instanceof SerFeatureTrace serFeatureTrace)){
            throw new RuntimeException("Cannot fuse SerFeatureTrace with non-SerFeatureTrace.");
        }
        this.addRetroactiveCondition(serFeatureTrace.retroactiveCondition);
        this.addProactiveCondition(serFeatureTrace.getProactiveCondition());
    }

    @Override
    public LogicCondition getOverallCondition(EvaluationStrategy evaluationStrategy) {
        if (this.retroactiveCondition == null && this.proactiveCondition == null){
            throw new RuntimeException("Neither retroactive nor proactive condition exists.");
        } else  {
            return evaluationStrategy.getOverallCondition(this.getProactiveCondition(), this.retroactiveCondition);
        }
    }

    @Override
    public void setRetroactiveCondition(LogicCondition retroactiveCondition) {
        this.retroactiveCondition = retroactiveCondition;
    }

    @Override
    public void setProactiveCondition(String proactiveConditionString) {
        this.setProactiveCondition(LogicCondition.of(proactiveConditionString == null ? null : FeatureLiterals.sanitize(proactiveConditionString)));
    }

    @Override
    public void setProactiveCondition(LogicCondition proactiveCondition) {
        this.proactive = sanitized(proactiveCondition);
        this.proactiveCondition = this.proactive == null ? null : this.proactive.text();
    }

    // "." and "-" cannot be parsed by LogicNG: feature names and revision ids are written with "_"
    // (FeatureLiterals) - so are conditions from outside ECCO
    private static LogicCondition sanitized(LogicCondition condition) {
        if (condition == null) { return null; }
        String text = FeatureLiterals.sanitize(condition.text());
        return text.equals(condition.text()) ? condition : LogicCondition.of(text);
    }

    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof SerFeatureTrace serFeatureTrace)) return false;
        return Objects.equals(this.node, serFeatureTrace.node)
                && Objects.equals(this.proactiveCondition, serFeatureTrace.proactiveCondition)
                && Objects.equals(this.retroactiveCondition, serFeatureTrace.retroactiveCondition);
    }

    @Override
    public int hashCode(){
        return Objects.hash(this.node, this.proactiveCondition, this.retroactiveCondition);
    }
}
