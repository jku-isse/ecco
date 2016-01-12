package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import javafx.scene.control.Label;
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


	public ArtifactDetailView(EccoService service) {
		this.service = service;
	}

	public void showTree(Node node) {
		// TODO: show some general info, like sequence graph if it is an ordered node.
		HBox detailView = new HBox(new Label("Detail View"));

		this.setTop(detailView);


		if (!this.initialized && this.service.isInitialized()) {
			this.service.getInjector().injectMembers(this);

			this.initialized = true;
		}

		//service.getInjector().injectMembers(this);
		//Set<ArtifactViewer> tempArtifactViewers = new HashSet<ArtifactViewer>();
		//Set<ArtifactViewer> artifactViewers = this.service.getInjector().getInstance(tempArtifactViewers.getClass());

		// TODO: pick appropriate artifact view from list also if a child node of an plugin artifact is selected.
		ArtifactViewer artifactViewer = null;
		for (ArtifactViewer tempArtifactViewer : artifactViewers) {
			if (tempArtifactViewer instanceof Pane) {
				if (node.getArtifact() != null && node.getArtifact().getData() instanceof PluginArtifactData) {
					PluginArtifactData pad = (PluginArtifactData) node.getArtifact().getData();
					if (tempArtifactViewer.getPluginId().equals(pad.getPluginId()))
						artifactViewer = tempArtifactViewer;
				}
			}
		}

		if (artifactViewer != null && artifactViewer instanceof Pane) {
			try {
				this.setCenter((Pane) artifactViewer);
				artifactViewer.showTree(node);
			} catch (Exception ex) {
				TextArea exceptionTextArea = new TextArea();
				exceptionTextArea.setEditable(false);

				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				ex.printStackTrace(pw);
				String exceptionText = sw.toString();

				exceptionTextArea.setText(exceptionText);
				this.setCenter(exceptionTextArea);
			}
		} else
			this.setCenter(null);

	}

}
