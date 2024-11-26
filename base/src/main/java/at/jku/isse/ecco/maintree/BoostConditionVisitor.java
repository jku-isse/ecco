package at.jku.isse.ecco.maintree;

import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.featuretrace.FeatureTrace;

public class BoostConditionVisitor implements Node.Op.NodeVisitor {

    // there must be a user condition and no other contradicting user condition in the association
    private boolean boostPossible = false;
    String conditionCandidate;

    // todo: create a visitor pattern that may stop visiting at some point
    @Override
    public void visit(Node.Op node) {
        FeatureTrace featureTrace = node.getFeatureTrace();
        if (featureTrace == null){
            return;
        }
        String userCondition = featureTrace.getUserConditionString();
        if (userCondition == null){
            return;
        } else if (this.conditionCandidate == null) {
            this.boostPossible = true;
            this.conditionCandidate = userCondition;
        } else if (!this.conditionCandidate.equals(userCondition)) {
            this.boostPossible = false;
        }
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
