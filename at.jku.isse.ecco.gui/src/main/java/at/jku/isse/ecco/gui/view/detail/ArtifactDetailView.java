package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.gui.view.graph.SequenceGraphView;
import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;
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

	private SequenceGraphView sequenceGraphView;


	public ArtifactDetailView(EccoService service) {
		this.service = service;

		this.sequenceGraphView = new SequenceGraphView();
	}

	public void showTree(Node node) {
		SplitPane splitPane = new SplitPane();
		this.setCenter(splitPane);


		SplitPane detailsSplitPane = new SplitPane();
		detailsSplitPane.setOrientation(Orientation.VERTICAL);
		splitPane.getItems().add(detailsSplitPane);

		// TODO: show some general info, like sequence graph if it is an ordered node.
		HBox detailView = new HBox(new Label("Detail View"));
		detailsSplitPane.getItems().add(detailView);

		// if node is an ordered node display its sequence graph
		if (node.getArtifact() != null && node.getArtifact().getSequenceGraph() != null) {
			detailsSplitPane.getItems().add(this.sequenceGraphView);
			this.sequenceGraphView.showGraph(node.getArtifact().getSequenceGraph());
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
					String pluginId = Trees.getPluginId(node);
					if (tempArtifactViewer.getPluginId().equals(pluginId))
						artifactViewer = tempArtifactViewer;

//					if (node.getArtifact() != null && node.getArtifact().getData() instanceof PluginArtifactData) {
//						PluginArtifactData pad = (PluginArtifactData) node.getArtifact().getData();
//						if (tempArtifactViewer.getPluginId().equals(pad.getPluginId()))
//							artifactViewer = tempArtifactViewer;
//					}
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

}
