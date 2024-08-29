package at.jku.isse.ecco.experiment.utils;

import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.tree.Node;

import java.util.Map;

public class LiteralCleanUpVisitor implements Node.Op.NodeVisitor {

    private final Map<String, String> literalNameMap;

    public LiteralCleanUpVisitor(Map<String, String> literalNameMap){
        this.literalNameMap = literalNameMap;
    }

    @Override
    public void visit(Node.Op node) {
        FeatureTrace trace = node.getFeatureTrace();
        for (String groundTruthName : this.literalNameMap.keySet()){
            this.replaceLiteralName(trace, groundTruthName);
        }
    }

    private void replaceLiteralName(FeatureTrace trace, String groundTruthName){
        String repoName = this.literalNameMap.get(groundTruthName);
        String diffCondition = trace.getDiffConditionString();
        String userCondition = trace.getUserConditionString();

        if (diffCondition != null){
            String cleanedDiffCondition = diffCondition.replace(repoName, groundTruthName);
            trace.setDiffCondition(cleanedDiffCondition);
        }

        if (userCondition != null){
            String cleanedUserCondition = userCondition.replace(repoName, groundTruthName);
            trace.setUserCondition(cleanedUserCondition);
        }
    }
}
