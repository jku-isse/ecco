package at.jku.isse.ecco.maintree.building;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.featuretrace.ProactiveTraceCheck;
import at.jku.isse.ecco.tree.Node;

import java.util.Collection;
import java.util.Map;

public interface MainTreeBuildingStrategy {
    Node.Op buildMainTree(Collection<Association.Op> associations);

    /**
     * Builds the main tree, checking proactive feature traces against the commit history with
     * {@code traceCheck} where the strategy uses them (boosting) - by default not at all.
     */
    default Node.Op buildMainTree(Collection<Association.Op> associations, ProactiveTraceCheck traceCheck) {
        return this.buildMainTree(associations);
    }

    /**
     * Builds the main tree with the retroactive conditions of some associations replaced: each
     * association whose id is a key of {@code retroactiveConditions} gets that condition on its
     * nodes instead of its own (in a copy - the associations are not changed). Used to check out with
     * minimized conditions, which are equivalent under the accepted feature model.
     */
    default Node.Op buildMainTree(Collection<Association.Op> associations, ProactiveTraceCheck traceCheck, Map<String, String> retroactiveConditions) {
        if (retroactiveConditions.isEmpty())
            return this.buildMainTree(associations, traceCheck);
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + " cannot replace retroactive conditions.");
    }
}
