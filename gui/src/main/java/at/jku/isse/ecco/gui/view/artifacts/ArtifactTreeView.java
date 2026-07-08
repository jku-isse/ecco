package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.gui.view.detail.ArtifactDetailView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.RootNode;
import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;

import java.util.Collection;

public class ArtifactTreeView extends BorderPane {
	private final ArtifactTreeTableView artifactTreeTableView;
	private final ArtifactDetailView artifactDetailView;

	public ArtifactTreeView(final EccoService service) {
		artifactDetailView = new ArtifactDetailView(service);

		// artifact tree table view
		this.artifactTreeTableView = new ArtifactTreeTableView();

		this.artifactTreeTableView.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null) {
				artifactDetailView.showTree(newValue.getValue());
			}
		});


		// split panes
		SplitPane artifactsSplitPane = new SplitPane();
		artifactsSplitPane.setOrientation(Orientation.HORIZONTAL);
		artifactsSplitPane.getItems().addAll(this.artifactTreeTableView, artifactDetailView);


		this.setCenter(artifactsSplitPane);
	}

	public void setRootNode(RootNode rootNode) {
		this.artifactDetailView.reset();
		this.artifactTreeTableView.setRootNode(rootNode);
	}

	public void setAssociationInfo(Collection<AssociationInfoImpl> associationInfos) {
		this.artifactDetailView.setAssociationInfo(associationInfos);
		this.artifactTreeTableView.setAssociationInfo(associationInfos);
	}

}
