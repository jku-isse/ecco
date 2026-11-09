package at.jku.isse.ecco.gui.common;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;

/** Which artifact plugin made a node of an artifact tree - for choosing the viewer that shows it. */
public final class PluginIds {

	private PluginIds() {
	}

	/**
	 * Retrieves the ID of the plugin that created the given node. If the node was created by an artifact plugin the plugin's ID is returned. If the node was not created by a plugin, as is for example the case with directories, null is returned.
	 *
	 * @param node The node for which the plugin ID shall be retrieved.
	 * @return The plugin ID of the given node or null if the node was not created by a plugin.
	 */
	public static String of(Node node) {
		if (node == null || node.getArtifact() == null)
			return null;
		else {
			if (node.getArtifact().getData() instanceof PluginArtifactData)
				return ((PluginArtifactData) node.getArtifact().getData()).getPluginId();
			else {
				return of(node.getParent());
			}
		}
	}
}
