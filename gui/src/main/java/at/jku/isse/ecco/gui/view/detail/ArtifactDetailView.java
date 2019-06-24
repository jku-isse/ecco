package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.gui.view.graph.PartialOrderGraphView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

public class ArtifactDetailView extends BorderPane {

	private EccoService service;

	private boolean initialized;

	@Inject
	private Set<ArtifactViewer> artifactViewers;

	private PartialOrderGraphView partialOrderGraphView;


	public ArtifactDetailView(EccoService service) {
		this.service = service;

		this.partialOrderGraphView = new PartialOrderGraphView();
	}


	public void showTree(Node node) {
		SplitPane splitPane = new SplitPane();
		splitPane.setOrientation(Orientation.HORIZONTAL);
		this.setCenter(splitPane);


		SplitPane detailsSplitPane = new SplitPane();
		detailsSplitPane.setOrientation(Orientation.VERTICAL);
		splitPane.getItems().add(detailsSplitPane);

		// TODO: show some general info
		HBox detailView = new HBox(new Label("Detail View"));
		detailsSplitPane.getItems().add(detailView);

		// if node is an ordered node display its sequence graph
		if (node.getArtifact() != null && node.getArtifact().getSequenceGraph() != null) {
			detailsSplitPane.getItems().add(this.partialOrderGraphView);
			this.partialOrderGraphView.showGraph(node.getArtifact().getSequenceGraph());
		}


		if (!this.initialized && this.service.isInitialized()) {
			this.service.getInjector().injectMembers(this);

			this.initialized = true;
		}

		if (this.initialized) {
			// select artifact viewer
			ArtifactViewer artifactViewer = null;
			for (ArtifactViewer tempArtifactViewer : artifactViewers) {
				if (tempArtifactViewer.getPluginId() != null && tempArtifactViewer instanceof Pane) {
					String pluginId = getPluginId(node);
					if (tempArtifactViewer.getPluginId().equals(pluginId))
						artifactViewer = tempArtifactViewer;
				}
			}

			// if an artifact viewer was found display it
			if (artifactViewer != null && artifactViewer instanceof Pane) {
				try {
					splitPane.getItems().add((Pane) artifactViewer);
					artifactViewer.showTree(node);
				} catch (Exception ex) {
					TextArea exceptionTextArea = new TextArea();
					exceptionTextArea.setEditable(false);

					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					ex.printStackTrace(pw);
					String exceptionText = sw.toString();

					exceptionTextArea.setText(exceptionText);
					splitPane.getItems().add(exceptionTextArea);
				}
			}
		}

	}


	/**
	 * Retrieves the ID of the plugin that created the given node. If the node was created by an artifact plugin the plugin's ID is returned. If the node was not creaetd by a plugin, as is for example the case with directories, null is returned.
	 *
	 * @param node The node for which the plugin ID shall be retrieved.
	 * @return The plugin ID of the given node or null if the node was not created by a plugin.
	 */
	public static String getPluginId(Node node) {
		if (node == null || node.getArtifact() == null)
			return null;
		else {
			if (node.getArtifact().getData() instanceof PluginArtifactData)
				return ((PluginArtifactData) node.getArtifact().getData()).getPluginId();
			else {
				return getPluginId(node.getParent());
			}
		}
	}

}
