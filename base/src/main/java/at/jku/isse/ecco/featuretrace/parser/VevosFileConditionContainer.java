package at.jku.isse.ecco.featuretrace.parser;

import java.util.Collection;
import java.util.HashSet;

public class VevosFileConditionContainer {

    Collection<VevosCondition> fileSpecificConditions;

    public VevosFileConditionContainer(Collection<VevosCondition> conditions){
        // a file without any conditions has none to match (this used to keep null and NPE on lookup)
        this.fileSpecificConditions = conditions == null ? java.util.List.of() : conditions;
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
