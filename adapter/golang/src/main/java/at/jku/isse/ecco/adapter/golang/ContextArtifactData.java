package at.jku.isse.ecco.adapter.golang;

import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;

/**
 * Used to add depth to the ECCO tree
 * Nodes that are not terminal nodes are represented by this ArtifactData type
 * This class does not need to hold any data, as it's only used for structure
 * @see GoReader#parseGoFile(Node.Op, Path)
 */
public class ContextArtifactData implements ArtifactData {
}
