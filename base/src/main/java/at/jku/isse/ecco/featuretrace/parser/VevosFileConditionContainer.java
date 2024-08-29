package at.jku.isse.ecco.featuretrace.parser;

import java.util.Collection;
import java.util.HashSet;

public class VevosFileConditionContainer {

    Collection<VevosCondition> fileSpecificConditions;

    public VevosFileConditionContainer(Collection<VevosCondition> conditions){
        this.fileSpecificConditions = conditions;
        if (conditions == null){
            System.out.println("this.");
        }
    }

    public Collection<VevosCondition> getMatchingPresenceConditions(int startLine, int endLine){
        Collection<VevosCondition> matchingConditions = new HashSet<>();
        for (VevosCondition condition : this.fileSpecificConditions){
            if (condition.getStartLine() <= startLine && condition.getEndLine() >= endLine){
                matchingConditions.add(condition);
            }
        }
        return matchingConditions;
    }
}
