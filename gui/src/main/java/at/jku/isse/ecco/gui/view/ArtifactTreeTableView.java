package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeTableCell;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ArtifactTreeTableView extends TreeTableView<ArtifactTreeTableView.NodeWrapper> {

	public ArtifactTreeTableView() {
		super();

		// create columns

		class ColorTreeTableCell<Inputs> extends TreeTableCell<Inputs, Color> {
			@Override
			protected void updateItem(Color item, boolean empty) {
				super.updateItem(item, empty);

				setText(null);
				setGraphic(null);

				this.setBackground(new Background(new BackgroundFill(item, null, null)));
			}
		}

		TreeTableColumn<NodeWrapper, Color> colorNodeCol = new TreeTableColumn<>();
		colorNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<NodeWrapper, Color> param) -> param.getValue().getValue().colorProperty());
		colorNodeCol.setCellFactory(new Callback<TreeTableColumn<NodeWrapper, Color>, TreeTableCell<NodeWrapper, Color>>() {
			@Override
			public TreeTableCell<NodeWrapper, Color> call(TreeTableColumn<NodeWrapper, Color> param) {
				return new ColorTreeTableCell<>();
			}
		});

		TreeTableColumn<NodeWrapper, String> labelNodeCol = new TreeTableColumn<>("Node");
		labelNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<NodeWrapper, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().toString()));

		TreeTableColumn<NodeWrapper, Boolean> orderedNodeCol = new TreeTableColumn<>("Ordered");
		orderedNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<NodeWrapper, Boolean> param) -> new ReadOnlyBooleanWrapper(param.getValue().getValue().getArtifact() != null && param.getValue().getValue().getArtifact().isOrdered()));

		TreeTableColumn<NodeWrapper, Boolean> atomicNodeCol = new TreeTableColumn<>("Atomic");
		atomicNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<NodeWrapper, Boolean> param) -> new ReadOnlyBooleanWrapper(param.getValue().getValue().isAtomic()));

		TreeTableColumn<NodeWrapper, Boolean> uniqueNodeCol = new TreeTableColumn<>("Unique");
		uniqueNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<NodeWrapper, Boolean> param) -> new ReadOnlyBooleanWrapper(param.getValue().getValue().isUnique()));

		TreeTableColumn<NodeWrapper, Integer> snNodeCol = new TreeTableColumn<>("Sequence Number");
		snNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<NodeWrapper, Integer> param) -> new ReadOnlyObjectWrapper<Integer>(param.getValue().getValue().getArtifact() == null ? -1 : param.getValue().getValue().getArtifact().getSequenceNumber()));

		TreeTableColumn<NodeWrapper, String> associationNodeCol = new TreeTableColumn<>("Association");
		associationNodeCol.setCellValueFactory(
				(TreeTableColumn.CellDataFeatures<NodeWrapper, String> param) ->
				{
					if (param.getValue().getValue().getArtifact() != null) {
						Association containingAssociation = param.getValue().getValue().getArtifact().getContainingNode().getContainingAssociation();
						if (containingAssociation != null)
							return new ReadOnlyStringWrapper(String.valueOf(containingAssociation.getId()));

					}
					return new ReadOnlyStringWrapper("null");
				}
		);

		TreeTableColumn<NodeWrapper, Boolean> isSelectedNodeCol = new TreeTableColumn<>("Selected");
		isSelectedNodeCol.setCellValueFactory(
				(TreeTableColumn.CellDataFeatures<NodeWrapper, Boolean> param) ->
				{
					Artifact<?> artifact = param.getValue().getValue().getArtifact();

					if (artifact != null) {
						SimpleBooleanProperty sbp = new SimpleBooleanProperty() {
							@Override
							public boolean get() {
								return super.get();
							}

							@Override
							public void set(boolean value) {
								if (value)
									artifact.putProperty(Artifact.PROPERTY_MARKED_FOR_EXTRACTION, value);
								else
									artifact.removeProperty(Artifact.PROPERTY_MARKED_FOR_EXTRACTION);
								super.set(value);
							}
						};
						sbp.set(artifact.getProperty(Artifact.PROPERTY_MARKED_FOR_EXTRACTION).isPresent());
						return sbp;
						//return new ReadOnlyBooleanWrapper(artifact.getProperty(Artifact.PROPERTY_MARKED_FOR_EXTRACTION).isPresent());
					} else {
						return new ReadOnlyBooleanWrapper(false);
					}
				}
		);
		isSelectedNodeCol.setCellFactory(CheckBoxTreeTableCell.forTreeTableColumn(isSelectedNodeCol));
		isSelectedNodeCol.setEditable(true);


		TreeTableColumn<NodeWrapper, String> artifactTreeCol = new TreeTableColumn<>("Artifact Tree");
		artifactTreeCol.getColumns().setAll(labelNodeCol, colorNodeCol, orderedNodeCol, atomicNodeCol, uniqueNodeCol, snNodeCol, associationNodeCol, isSelectedNodeCol);


		uniqueNodeCol.setCellFactory(new Callback<TreeTableColumn<NodeWrapper, Boolean>, TreeTableCell<NodeWrapper, Boolean>>() {
			public TreeTableCell<NodeWrapper, Boolean> call(TreeTableColumn param) {
				return new TreeTableCell<NodeWrapper, Boolean>() {
					@Override
					public void updateItem(Boolean item, boolean empty) {
						super.updateItem(item, empty);
						if (!isEmpty()) {
							if (item) {
								this.setTextFill(Color.GREEN);
//								getTreeTableRow().getStyleClass().add("uniquerow");
//								getTreeTableRow().setTextFill(Color.BLACK);
							} else {
								this.setTextFill(Color.RED);
//								getTreeTableRow().getStyleClass().add("nonuniquerow");
//								getTreeTableRow().setTextFill(Color.GRAY);
							}
							this.setText(item.toString());
						} else {
							this.setText("");
						}
					}
				};
			}
		});

		this.setRowFactory(tv -> new TreeTableRow<NodeWrapper>() {
			@Override
			public void updateItem(NodeWrapper item, boolean empty) {
				super.updateItem(item, empty);
				if (item != null && item.isUnique()) {
					this.getStyleClass().add("uniquerow");
					this.setTextFill(Color.BLACK);
				} else {
					this.getStyleClass().add("nonuniquerow");
					this.setTextFill(Color.GRAY);
				}
			}
		});


		this.getColumns().setAll(artifactTreeCol);

		this.setEditable(true);
		this.setTableMenuButtonVisible(true);
		this.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
		this.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
	}

	public ArtifactTreeTableView(RootNode rootNode) {
		this();

		this.setRootNode(rootNode);
	}


	public void setRootNode(RootNode rootNode) {
		if (rootNode == null)
			this.setRoot(null);
		else
			this.setRoot(new NodeTreeItem(new NodeWrapper(rootNode)));
	}

	public void markSelected() {
		for (TreeItem<NodeWrapper> item : this.getSelectionModel().getSelectedItems()) {
			Artifact<?> artifact = item.getValue().getArtifact();
			if (artifact != null) {
				artifact.putProperty(Artifact.PROPERTY_MARKED_FOR_EXTRACTION, true);
			}
		}
	}


	private Collection<ArtifactsView.AssociationInfo> associationInfos = null;

	public void setAssociationInfo(Collection<ArtifactsView.AssociationInfo> associationInfos) {
		this.associationInfos = associationInfos;
	}


	public class NodeTreeItem extends TreeItem<NodeWrapper> {
		private boolean firstTimeChildren = true;

		public NodeTreeItem(NodeWrapper node) {
			super(node);

			if (ArtifactTreeTableView.this.associationInfos != null) {
				if (node != null && node.getArtifact() != null && node.getArtifact().getContainingNode() != null) {
					Association nodeAssociation = node.getArtifact().getContainingNode().getContainingAssociation();
					if (nodeAssociation != null) {
						Optional<ArtifactsView.AssociationInfo> opt = ArtifactTreeTableView.this.associationInfos.stream().filter(o -> o.getAssociation() == nodeAssociation).findFirst();
						opt.ifPresent(associationInfo -> node.colorProperty().bind(associationInfo.colorProperty()));
					}
				}
			}
		}

		@Override
		public ObservableList<TreeItem<NodeWrapper>> getChildren() {
			if (this.firstTimeChildren) {
				this.firstTimeChildren = false;

				for (Node child : this.getValue().getChildren()) {
					super.getChildren().add(new NodeTreeItem(new NodeWrapper(child)));
				}
			}
			return super.getChildren();
		}

		@Override
		public boolean isLeaf() {
			return this.getValue().getChildren().isEmpty();
		}
	}

	public class NodeWrapper implements Node {

		private ObjectProperty<Color> color;

		public NodeWrapper(Node node) {
			this.node = node;
			this.color = new SimpleObjectProperty<Color>(Color.TRANSPARENT);
		}

		public ObjectProperty<Color> colorProperty() {
			return this.color;
		}


		private Node node;

		@Override
		public int hashCode() {
			return node.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return node.equals(obj);
		}

		@Override
		public String getNodeString() {
			return node.getNodeString();
		}

		@Override
		public String toString() {
			return node.toString();
		}

		@Override
		public void traverse(NodeVisitor visitor) {
			node.traverse(visitor);
		}

		@Override
		public boolean isAtomic() {
			return node.isAtomic();
		}

		@Override
		public Association getContainingAssociation() {
			return node.getContainingAssociation();
		}

		@Override
		public Artifact<?> getArtifact() {
			return node.getArtifact();
		}

		@Override
		public Node getParent() {
			return node.getParent();
		}

		@Override
		public boolean isUnique() {
			return node.isUnique();
		}

		@Override
		public List<? extends Node> getChildren() {
			return node.getChildren();
		}

		@Override
		public int countArtifacts() {
			return node.countArtifacts();
		}

		@Override
		public int computeDepth() {
			return node.computeDepth();
		}

		@Override
		public Map<Integer, Integer> countArtifactsPerDepth() {
			return node.countArtifactsPerDepth();
		}

		@Override
		public void print() {
			node.print();
		}

		@Override
		public Map<String, Object> getProperties() {
			return node.getProperties();
		}

		@Override
		public <T> Optional<T> getProperty(String name) {
			return node.getProperty(name);
		}

		@Override
		public <T> void putProperty(String name, T property) {
			node.putProperty(name, property);
		}

		@Override
		public void removeProperty(String name) {
			node.removeProperty(name);
		}
	}

}
