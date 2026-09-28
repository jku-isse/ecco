package at.jku.isse.ecco.maintree.building;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.featuretrace.ProactiveTraceCheck;
import at.jku.isse.ecco.tree.Node;

import java.util.Collection;

public interface MainTreeBuildingStrategy {
    Node.Op buildMainTree(Collection<Association.Op> associations);

    /**
     * Builds the main tree, checking proactive feature traces against the commit history with
     * {@code traceCheck} where the strategy uses them (boosting) - by default not at all.
     */
    default Node.Op buildMainTree(Collection<Association.Op> associations, ProactiveTraceCheck traceCheck) {
        return this.buildMainTree(associations);
    }
}
