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
		this.artifactTreeTableView = new ArtifactTreeTableView(service);

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
		// captured BEFORE reset() below throws away every Tab instance - see
		// ArtifactDetailView.getSelectedTabKey()'s javadoc for why this can't be recovered after
		// the fact via a selection listener
		String previouslySelectedTabKey = this.artifactDetailView.getSelectedTabKey();

		this.artifactDetailView.reset();
		this.artifactTreeTableView.setRootNode(rootNode);

		// auto-show a sensible default node's code instead of requiring a manual click on a tree
		// row first - the first node below the root with a registered viewer, found in the
		// background, holding the service's lock (it walks the tree).
		// Falls back to the root node itself if nothing has a registered viewer (e.g. an adapter
		// with no dedicated viewer plugin) - its Info tab is still shown, a strictly better default
		// than showing nothing at all.
		this.artifactDetailView.showFirstViewable(rootNode, previouslySelectedTabKey);
	}

	/** What the empty tree says - where its artifacts come from differs between the tabs that show one. */
	public void setPlaceholder(String text) {
		this.artifactTreeTableView.setPlaceholder(new javafx.scene.control.Label(text));
	}

	public void setAssociationInfo(Collection<AssociationInfoImpl> associationInfos) {
		this.artifactDetailView.setAssociationInfo(associationInfos);
		this.artifactTreeTableView.setAssociationInfo(associationInfos);
	}

}
