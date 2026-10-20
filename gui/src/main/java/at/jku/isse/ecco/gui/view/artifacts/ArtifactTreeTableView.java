package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.RepositoryAccess;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.util.*;

/**
 * An artifact tree - a commit's, or the composition of the ticked associations - as a table. Its
 * nodes are the repository's (a composition's reach into the associations' trees), which a commit,
 * pull or import changes holding the service's lock: what a row shows is read in the background,
 * holding it (see {@link NodeWrapper}), a level at a time - the levels opened first when a tree is
 * shown, then each level when it is opened. They were read on the FX thread, by the cells and the
 * opened items, while a commit changed them. A level at a time, not the whole tree copied at once:
 * a tree can have hundreds of thousands of nodes (an image's pixels), of which a few are looked at.
 */
public class ArtifactTreeTableView extends TreeTableView<ArtifactTreeTableView.NodeWrapper> {

	private final EccoService service;

	// referenced again from setRootNode() below to re-fit their width to the newly-loaded tree, so
	// (unlike every other column here, which is only ever touched from inside the constructor)
	// these two need to be fields rather than constructor-local variables.
	private final TreeTableColumn<NodeWrapper, Integer> snNodeCol;
	private final TreeTableColumn<NodeWrapper, String> associationNodeCol;

	/** Counts {@link #setRootNode} calls: only the latest tree read is shown. FX thread only. */
	private long requests;
	private boolean reading;

	public ArtifactTreeTableView(EccoService service) {
		super();
		this.service = service;

		// create columns

		TreeTableColumn<NodeWrapper, String> labelNodeCol = new TreeTableColumn<>("Node");
		labelNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<NodeWrapper, String> param) -> new ReadOnlyStringWrapper(param.getValue().getValue().toString()));
		labelNodeCol.setCellFactory(param -> new TreeTableCell<NodeWrapper, String>() {
			{
				// grows the row instead of the column when a node's text is long - same intent as
				// TableColumns.wrappingCellFactory(), inlined here since this cell factory also
				// needs its own background-coloring behavior below
				setWrapText(true);
			}

			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				backgroundProperty().unbind();

				if (empty || item == null) {
					setText(null);
					setBackground(null);
				} else {
					setText(item);
					TreeTableRow<NodeWrapper> row = getTreeTableRow();
					NodeWrapper wrapper = row == null ? null : row.getItem();
					if (wrapper != null) {
						backgroundProperty().bind(Bindings.createObjectBinding(
								() -> new Background(new BackgroundFill(wrapper.colorProperty().getValue(), null, null)),
								wrapper.colorProperty()));
					} else {
						setBackground(null);
					}
				}
			}
		});

		TreeTableColumn<NodeWrapper, Boolean> orderedNodeCol = new TreeTableColumn<>("Ordered");
		// the values a row's node had when it was read (see NodeWrapper), not the live node's
		orderedNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<NodeWrapper, Boolean> param) -> new ReadOnlyBooleanWrapper(param.getValue().getValue().isOrdered()));

		TreeTableColumn<NodeWrapper, Boolean> atomicNodeCol = new TreeTableColumn<>("Atomic");
		atomicNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<NodeWrapper, Boolean> param) -> new ReadOnlyBooleanWrapper(param.getValue().getValue().isAtomic()));

		TreeTableColumn<NodeWrapper, Boolean> uniqueNodeCol = new TreeTableColumn<>("Unique");
		uniqueNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<NodeWrapper, Boolean> param) -> new ReadOnlyBooleanWrapper(param.getValue().getValue().isUnique()));

		this.snNodeCol = new TreeTableColumn<>("Sequence Number");
		snNodeCol.setCellValueFactory((TreeTableColumn.CellDataFeatures<NodeWrapper, Integer> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getValue().getSequenceNumber()));

		this.associationNodeCol = new TreeTableColumn<>("Association");
		associationNodeCol.setCellValueFactory(
				(TreeTableColumn.CellDataFeatures<NodeWrapper, String> param) -> {
					Association containingAssociation = param.getValue().getValue().getArtifactAssociation();
					return new ReadOnlyStringWrapper(containingAssociation == null ? "null" : String.valueOf(containingAssociation.getId()));
				}
		);





		uniqueNodeCol.setCellFactory(new Callback<>() {
			public TreeTableCell<NodeWrapper, Boolean> call(TreeTableColumn param) {
				return new TreeTableCell<>() {
					@Override
					public void updateItem(Boolean item, boolean empty) {
						super.updateItem(item, empty);
						if (!isEmpty()) {
							if (item) {
								this.setTextFill(Color.GREEN);
							} else {
								this.setTextFill(Color.RED);
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
				// updateItem() re-fires on every selection change too (not just when a recycled
				// row is reused for a different tree item), so the previous class must be removed
				// first - otherwise a row can accumulate both, and whichever rule happens to sort
				// last in the stylesheet wins regardless of this row's actual current state
				this.getStyleClass().removeAll("uniquerow", "nonuniquerow");
				if (item != null && item.isUnique()) {
					this.getStyleClass().add("uniquerow");
				} else {
					this.getStyleClass().add("nonuniquerow");
				}
			}
		});


		this.getColumns().setAll(List.of(labelNodeCol, orderedNodeCol, atomicNodeCol, uniqueNodeCol, snNodeCol, associationNodeCol));

		this.setTableMenuButtonVisible(true);
		this.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		// the tree's internals, hidden at first (the table menu, top right, shows them): they took
		// three quarters of the width, and the code was cut off
		for (TreeTableColumn<NodeWrapper, ?> internal : List.of(orderedNodeCol, atomicNodeCol, uniqueNodeCol, snNodeCol))
			internal.setVisible(false);
		// the association by its first characters, as the associations table shows it; the root has none
		associationNodeCol.setCellFactory(column -> new TreeTableCell<>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				boolean none = empty || item == null || "null".equals(item);
				setText(none ? null : TableColumns.shortIds(item));
				setTooltip(none ? null : new Tooltip(item));
			}
		});
		this.setPlaceholder(new Label("Tick associations above, or tick features in the Features pane, to see their artifacts."));

		TableColumns.controlWidth(orderedNodeCol);
		TableColumns.controlWidth(atomicNodeCol);
		TableColumns.controlWidth(uniqueNodeCol);
		// sized by their content when a tree is shown; until then by their headers
		TableColumns.defaultWidth(snNodeCol, 0);
		TableColumns.defaultWidth(associationNodeCol, 0);
		TableColumns.growToFill(this, labelNodeCol);
	}

	public ArtifactTreeTableView(EccoService service, RootNode rootNode) {
		this(service);

		this.setRootNode(rootNode);
	}


	/**
	 * At most this many rows are opened when a tree is shown - breadth first, so the directories and
	 * files come first; the rest opens on a click.
	 */
	static final int MAX_EXPANDED_ROWS = 2_000;

	/**
	 * Shows [rootNode], opened breadth first up to {@link #MAX_EXPANDED_ROWS} rows - once they are
	 * read, in the background, holding the service's lock; until then the tree shown before stays.
	 * It was opened all through after being attached, each row added on its own: the table walked
	 * every opened row again for each - quadratic, 100 s for a tinycc commit of 57,000 artifacts, on
	 * the FX thread. Opened while detached, children added at once, so far only.
	 */
	public final void setRootNode(RootNode rootNode) {
		long request = ++this.requests;
		if (rootNode == null) {
			this.reading = false;
			this.setRoot(null);
			return;
		}
		this.reading = true;
		RepositoryAccess.read(this.service, "Artifact tree", svc -> this.readOpened(rootNode, MAX_EXPANDED_ROWS),
				opened -> {
					if (request != this.requests)
						return;
					this.reading = false;
					TreeItem<NodeWrapper> root = this.itemOf(opened);
					this.setRoot(root);

					TableColumns.fitToContent(snNodeCol, root);
				},
				failure -> {
					if (request != this.requests)
						return;
					this.reading = false;
					new ExceptionAlert(failure).show();
				});
	}

	/** Whether the tree last set is still being read - for tests. */
	public boolean isReading() {
		return this.reading;
	}

	/** A node read, its children too if read; opened, if they are. */
	private static final class Read {
		final NodeWrapper wrapper;
		List<Read> children;

		Read(NodeWrapper wrapper) {
			this.wrapper = wrapper;
		}
	}

	/**
	 * Reads [root] and below, breadth first, the children of each node while the rows opened stay
	 * within [budget] - the nodes' further children are read when they are opened. Holding the
	 * service's lock.
	 */
	private Read readOpened(Node root, int budget) {
		Read top = new Read(new NodeWrapper(root));
		java.util.ArrayDeque<Read> queue = new java.util.ArrayDeque<>();
		queue.add(top);
		int rows = 1;
		while (!queue.isEmpty()) {
			Read item = queue.poll();
			if (item.wrapper.isLeaf())
				continue;
			List<? extends Node> children = item.wrapper.getNode().getChildren();
			if (rows + children.size() > budget)
				break;
			item.children = new ArrayList<>();
			for (Node child : children)
				item.children.add(new Read(new NodeWrapper(child)));
			rows += children.size();
			queue.addAll(item.children);
		}
		return top;
	}

	/** The item for [read], opened where its children were read. FX thread. */
	private TreeItem<NodeWrapper> itemOf(Read read) {
		NodeTreeItem item = new NodeTreeItem(read.wrapper);
		if (read.children != null) {
			// at once: one change for the table, not one per child
			List<TreeItem<NodeWrapper>> children = new ArrayList<>();
			for (Read child : read.children)
				children.add(this.itemOf(child));
			item.setChildrenRead(children);
			item.setExpanded(true);
		}
		return item;
	}

	private Collection<AssociationInfoImpl> associationInfos = null;

	/** The association infos by association, for each row's colour - every row searched all of them. */
	private java.util.Map<Association, AssociationInfoImpl> associationInfoByAssociation = new java.util.IdentityHashMap<>();

	public void setAssociationInfo(Collection<AssociationInfoImpl> associationInfos) {
		this.associationInfos = associationInfos;
		this.associationInfoByAssociation = new java.util.IdentityHashMap<>();
		if (associationInfos != null)
			for (AssociationInfoImpl info : associationInfos)
				this.associationInfoByAssociation.putIfAbsent(info.getAssociation(), info);
	}


	public class NodeTreeItem extends TreeItem<NodeWrapper> {
		private boolean childrenRead;
		private boolean readingChildren;

		public NodeTreeItem(NodeWrapper node) {
			super(node);

			if (ArtifactTreeTableView.this.associationInfos != null && node != null) {
				Association nodeAssociation = node.getArtifactAssociation();
				if (nodeAssociation != null) {
					AssociationInfoImpl associationInfo = ArtifactTreeTableView.this.associationInfoByAssociation.get(nodeAssociation);
					if (associationInfo != null)
						node.colorProperty().bind(associationInfo.colorProperty());
				}
			}
		}

		/**
		 * The children: when first asked for (the item is opened), read in the background, holding the
		 * service's lock - none until then.
		 */
		@Override
		public ObservableList<TreeItem<NodeWrapper>> getChildren() {
			if (!this.childrenRead && !this.readingChildren && !this.isLeaf()) {
				this.readingChildren = true;
				Node node = this.getValue().getNode();
				RepositoryAccess.read(ArtifactTreeTableView.this.service, "Artifact tree", svc -> {
							List<NodeWrapper> children = new ArrayList<>();
							for (Node child : node.getChildren())
								children.add(new NodeWrapper(child));
							return children;
						},
						children -> {
							List<TreeItem<NodeWrapper>> items = new ArrayList<>();
							for (NodeWrapper child : children)
								items.add(new NodeTreeItem(child));
							this.setChildrenRead(items);
						},
						failure -> {
							this.readingChildren = false;
							new ExceptionAlert(failure).show();
						});
			}
			return super.getChildren();
		}

		void setChildrenRead(List<TreeItem<NodeWrapper>> children) {
			this.childrenRead = true;
			this.readingChildren = false;
			super.getChildren().setAll(children);
		}

		@Override
		public boolean isLeaf() {
			return this.getValue().isLeaf();
		}
	}

	/**
	 * A node of the tree, with what its row shows read when the wrapper is made - holding the
	 * service's lock (see {@link #setRootNode}): the cells read it on the FX thread. The {@link Node}
	 * methods are the live node's: call them holding the lock, as the details view does.
	 */
	public class NodeWrapper implements Node {

		private static final long serialVersionUID = 1L;

		private final ObjectProperty<Color> color;

		private final String label;
		private final boolean ordered;
		private final boolean atomic;
		private final boolean unique;
		private final int sequenceNumber;
		private final Association artifactAssociation;
		private final boolean leaf;
		private final int hash;

		/** Reads what the row shows of [node]: call it holding the service's lock. */
		public NodeWrapper(Node node) {
			this.node = node;
			this.color = new SimpleObjectProperty<>(Color.TRANSPARENT);
			Artifact<?> artifact = node.getArtifact();
			this.label = node.toString();
			this.ordered = artifact != null && artifact.isOrdered();
			this.atomic = node.isAtomic();
			this.unique = node.isUnique();
			this.sequenceNumber = artifact == null ? -1 : artifact.getSequenceNumber();
			this.artifactAssociation = artifact == null || artifact.getContainingNode() == null ? null : artifact.getContainingNode().getContainingAssociation();
			this.leaf = node.getChildren().isEmpty();
			this.hash = node.hashCode();
		}

		/** Whether the node's artifact orders its children, when read. */
		public boolean isOrdered() {
			return this.ordered;
		}

		/** The sequence number of the node's artifact when read, -1 without one. */
		public int getSequenceNumber() {
			return this.sequenceNumber;
		}

		/** The association that held the node's artifact when read, null if none (e.g. no artifact). */
		public Association getArtifactAssociation() {
			return this.artifactAssociation;
		}

		/** Whether the node had no children when read. */
		public boolean isLeaf() {
			return this.leaf;
		}

		public ObjectProperty<Color> colorProperty() {
			return this.color;
		}


		private Node node;

		@Override
		public Node getNode() { return node; }

		@Override
		public int hashCode() {
			return hash;
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
			return label;
		}

		@Override
		public void traverse(NodeVisitor visitor) {
			node.traverse(visitor);
		}

		@Override
		public boolean isAtomic() {
			return atomic;
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
			return unique;
		}

		@Override
		public List<? extends Node> getChildren() { return node.getChildren(); }

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
