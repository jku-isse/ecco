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
        String retroactiveCondition = trace.getRetroactiveConditionString();
        String proactiveCondition = trace.getProactiveConditionString();
        
        if (retroactiveCondition != null){
            if (retroactiveCondition.contains("||") && retroactiveCondition.contains(this.baseFeatureName)){
                throw new RuntimeException("seems to be possible for base to be in disjunction.");
            }
            String cleanedRetroactiveCondition = retroactiveCondition.replace(this.baseFeatureName, this.TRUE_CONSTANT);
            trace.setRetroactiveCondition(cleanedRetroactiveCondition);
        }
        
        if (proactiveCondition != null){
            if (proactiveCondition.contains("||") && proactiveCondition.contains(this.baseFeatureName)){
                throw new RuntimeException("seems to be possible for base to be in disjunction.");
            }
            String cleanedProactiveCondition = proactiveCondition.replace(this.baseFeatureName, this.TRUE_CONSTANT);
            trace.setProactiveCondition(cleanedProactiveCondition);
        }
    }
}
