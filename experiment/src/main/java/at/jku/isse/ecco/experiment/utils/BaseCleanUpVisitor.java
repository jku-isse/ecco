package at.jku.isse.ecco.experiment.utils;

import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.tree.Node;

public class BaseCleanUpVisitor implements Node.Op.NodeVisitor {

    private String baseFeatureName;
    private final String TRUE_CONSTANT = "$true";
    private final String FALSE_CONSTANT = "$false";
    

    public BaseCleanUpVisitor(String baseFeatureName){
        this.baseFeatureName = baseFeatureName;
    }

    @Override
    public void visit(Node.Op node) {
        FeatureTrace trace = node.getFeatureTrace();
        String diffCondition = trace.getDiffConditionString();
        String userCondition = trace.getUserConditionString();
        
        if (diffCondition != null){
            if (diffCondition.contains("||") && diffCondition.contains(this.baseFeatureName)){
                throw new RuntimeException("seems to be possible for base to be in disjunction.");
            }
            String cleanedDiffCondition = diffCondition.replace(this.baseFeatureName, this.TRUE_CONSTANT);
            trace.setDiffCondition(cleanedDiffCondition);
        }
        
        if (userCondition != null){
            if (userCondition.contains("||") && userCondition.contains(this.baseFeatureName)){
                throw new RuntimeException("seems to be possible for base to be in disjunction.");
            }
            String cleanedUserCondition = userCondition.replace(this.baseFeatureName, this.TRUE_CONSTANT);
            trace.setUserCondition(cleanedUserCondition);
        }
    }
}
