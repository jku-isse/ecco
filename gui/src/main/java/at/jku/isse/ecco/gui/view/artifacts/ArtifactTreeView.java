package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.gui.view.detail.ArtifactDetailView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
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
		// captured BEFORE reset() below throws away every Tab instance - see
		// ArtifactDetailView.getSelectedTabKey()'s javadoc for why this can't be recovered after
		// the fact via a selection listener
		String previouslySelectedTabKey = this.artifactDetailView.getSelectedTabKey();

		this.artifactDetailView.reset();
		this.artifactTreeTableView.setRootNode(rootNode);

		// auto-show a sensible default node's code instead of requiring a manual click on a tree
		// row first - findNodeWithArtifactViewerRec() walks the whole (sub)tree looking for the
		// first node with a registered viewer, but setRootNode() above already unavoidably does a
		// full tree walk of its own via expandAll(), so this doesn't introduce a new class of cost.
		// Falls back to the root node itself if nothing has a registered viewer (e.g. an adapter
		// with no dedicated viewer plugin) - showTree() still populates the Info tab for any
		// non-null node, so this is a strictly better default than showing nothing at all.
		Node defaultNode = rootNode;
		if (rootNode != null) {
			Node withViewer = ArtifactDetailView.findNodeWithArtifactViewerRec(this.artifactDetailView, rootNode);
			if (withViewer != null) {
				defaultNode = withViewer;
			}
		}
		this.artifactDetailView.showTree(defaultNode, previouslySelectedTabKey);
	}

	public void setAssociationInfo(Collection<AssociationInfoImpl> associationInfos) {
		this.artifactDetailView.setAssociationInfo(associationInfos);
		this.artifactTreeTableView.setAssociationInfo(associationInfos);
	}

}
