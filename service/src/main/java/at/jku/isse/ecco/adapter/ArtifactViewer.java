package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.tree.Node;

/**
 * Interface to show subtree of a given node by a plugin.
 * ArtifactDetailView of ecco-gui tries to inject implementations of this interface and adds it
 * to the view if the implementation extends from Pane.
 */
public interface ArtifactViewer {

	public abstract String getPluginId();

	public void showTree(Node node);

}
