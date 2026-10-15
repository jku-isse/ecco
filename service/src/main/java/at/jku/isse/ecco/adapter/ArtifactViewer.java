package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.tree.Node;

import java.util.Collections;
import java.util.List;

/**
 * Interface to show subtree of a given node by a plugin.
 * ArtifactDetailView of ecco-gui tries to inject implementations of this interface and adds it
 * to the view if the implementation class extends from Pane.
 */
public interface ArtifactViewer {

	public abstract String getPluginId();

	public void showTree(Node node);

	/** The name of its tab in the artifact details: "Code", unless it shows something else (a score, an image). */
	default String getTabName() {
		return "Code";
	}

	/**
	 * The source text each of [nodes] stands for, as this viewer shows it - for an adapter whose
	 * artifacts don't hold their text themselves (see SourceSpanViewer). null for a node it cannot
	 * tell, the default for all.
	 */
	default List<String> sourceTexts(List<? extends Node> nodes) {
		return Collections.nCopies(nodes.size(), null);
	}

}
