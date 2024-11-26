package at.jku.isse.ecco.maintree;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;

import java.util.Collection;

public interface MainTreeBuildingStrategy {
    Node.Op buildMainTree(Collection<Association.Op> associations);
}
