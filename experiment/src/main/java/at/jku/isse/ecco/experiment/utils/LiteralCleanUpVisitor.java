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
        if (trace == null){
            return;
        }

        String repoName = this.literalNameMap.get(groundTruthName);
        String retroactiveCondition = trace.getRetroactiveConditionString();
        String proactiveCondition = trace.getProactiveConditionString();

        if (retroactiveCondition != null){
            String cleanedRetroactiveCondition = retroactiveCondition.replace(repoName, groundTruthName);
            trace.setRetroactiveCondition(cleanedRetroactiveCondition);
        }

        if (proactiveCondition != null){
            String cleanedProactiveCondition = proactiveCondition.replace(repoName, groundTruthName);
            trace.setProactiveCondition(cleanedProactiveCondition);
        }
    }
}
