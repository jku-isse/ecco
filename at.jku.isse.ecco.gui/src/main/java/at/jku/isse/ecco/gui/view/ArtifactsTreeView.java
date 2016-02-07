package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import static com.google.common.base.Preconditions.checkNotNull;

public class ArtifactsTreeView extends TreeTableView<Node> {

	public ArtifactsTreeView() {
		super();

		// create columns

		TreeTableColumn<Node, String> labelNodeCol = new TreeTableColumn<>("Node");
		labelNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Node, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().toString()));

		TreeTableColumn<Node, Boolean> orderedNodeCol = new TreeTableColumn<>("Ordered");
		orderedNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Node, Boolean> param) -> new ReadOnlyBooleanWrapper(param.getValue().getValue().getArtifact() != null && param.getValue().getValue().getArtifact().isOrdered()));

		TreeTableColumn<Node, Boolean> atomicNodeCol = new TreeTableColumn<>("Atomic");
		atomicNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Node, Boolean> param) -> new ReadOnlyBooleanWrapper(param.getValue().getValue().isAtomic()));

		TreeTableColumn<Node, Boolean> uniqueNodeCol = new TreeTableColumn<>("Unique");
		uniqueNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Node, Boolean> param) -> new ReadOnlyBooleanWrapper(param.getValue().getValue().isUnique()));

		TreeTableColumn<Node, Integer> snNodeCol = new TreeTableColumn<>("Sequence Number");
		snNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<Node, Integer> param) -> new ReadOnlyObjectWrapper<Integer>(param.getValue().getValue().getArtifact() == null ? -1 : param.getValue().getValue().getArtifact().getSequenceNumber()));

		TreeTableColumn<Node, String> associationNodeCol = new TreeTableColumn<>("Association");
		associationNodeCol.setCellValueFactory(
				(TreeTableColumn.CellDataFeatures<Node, String> param) ->
				{
					if (param.getValue().getValue().getArtifact() != null) {
						Association containingAssociation = param.getValue().getValue().getArtifact().getContainingNode().getContainingAssociation();
						if (containingAssociation != null)
							return new ReadOnlyStringWrapper(String.valueOf(containingAssociation.getId()));

					}
					return new ReadOnlyStringWrapper("null");
				}
		);


		TreeTableColumn<Node, String> artifactTreeCol = new TreeTableColumn<>("Artifact Tree");
		artifactTreeCol.getColumns().setAll(labelNodeCol, orderedNodeCol, atomicNodeCol, uniqueNodeCol, snNodeCol, associationNodeCol);


		uniqueNodeCol.setCellFactory(new Callback<TreeTableColumn<Node, Boolean>, TreeTableCell<Node, Boolean>>() {
			public TreeTableCell call(TreeTableColumn param) {
				return new TreeTableCell<Node, Boolean>() {
					@Override
					public void updateItem(Boolean item, boolean empty) {
						super.updateItem(item, empty);
						if (!isEmpty()) {
							if (item)
								this.setTextFill(Color.GREEN);
							else
								this.setTextFill(Color.RED);
							this.setText(item.toString());
						} else {
							this.setText("");
						}
					}
				};
			}
		});


		this.getColumns().setAll(artifactTreeCol);

		this.setTableMenuButtonVisible(true);
		this.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
	}

	public ArtifactsTreeView(RootNode rootNode) {
		this();

		this.setRootNode(rootNode);
	}


	public void setRootNode(RootNode rootNode) {
		checkNotNull(rootNode);

		this.setRoot(new NodeTreeItem(rootNode));
	}


	public class NodeTreeItem extends TreeItem<Node> {
		private boolean firstTimeChildren = true;

		public NodeTreeItem(Node node) {
			super(node);
		}

		@Override
		public ObservableList<TreeItem<Node>> getChildren() {
			if (this.firstTimeChildren) {
				this.firstTimeChildren = false;

				for (Node child : this.getValue().getChildren()) {
					super.getChildren().add(new NodeTreeItem(child));
				}
			}
			return super.getChildren();
		}

		@Override
		public boolean isLeaf() {
			return this.getValue().getChildren().isEmpty();
		}
	}

}
