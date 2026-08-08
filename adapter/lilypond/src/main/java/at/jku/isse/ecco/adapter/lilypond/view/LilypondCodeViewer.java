package at.jku.isse.ecco.adapter.lilypond.view;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.LilypondPlugin;
import at.jku.isse.ecco.adapter.lilypond.data.context.BaseContextArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.beans.PropertyChangeListener;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

/**
 * Shows a lilypond file's token tree with syntax highlighting. A commit (or any other composed
 * view) can span multiple lilypond files, so {@link #showTree(Node)} walks up to the true root of
 * whatever tree was passed in, finds every distinct lilypond file reachable from there (each is its
 * own {@link PluginArtifactData} node), and shows each as its own tab (each with its own background
 * parse task and position cache, mirroring the previous single-file behavior), defaulting to the
 * tab/node that was actually selected. If only one file is present, the tab chrome is skipped.
 */
public class LilypondCodeViewer extends BorderPane implements AssociationInfoArtifactViewer {

	private final HashMap<String, AssociationInfo> associationInfos;
	private final HashMap<String, PropertyChangeListener> associationListeners;
	private final Map<Path, FileView> fileViews = new LinkedHashMap<>();
	private final Callback<ListView<NodeTextBlock[]>, ListCell<NodeTextBlock[]>> cellFactory;
	private final TextArea taInfo;
	private final SplitPane splitPane;
	private final String style;

	public LilypondCodeViewer() {
		associationInfos = new HashMap<>();
		associationListeners = new HashMap<>();

		URL url = ClassLoader.getSystemResource("styles/LilypondCodeViewer.css");
		style = url != null ? url.toExternalForm() : null;

		cellFactory = createCellFactory();

		taInfo = new TextArea();
		taInfo.setMinHeight(60);
		taInfo.setWrapText(true);

		SplitPane.setResizableWithParent(taInfo, false);
		splitPane = new SplitPane(createListView(FXCollections.observableArrayList()), taInfo);
		splitPane.setOrientation(Orientation.VERTICAL);

		this.setCenter(splitPane);

		Platform.runLater(() -> splitPane.setDividerPositions(0.95));
	}

	private ListView<NodeTextBlock[]> createListView(ObservableList<NodeTextBlock[]> lines) {
		ListView<NodeTextBlock[]> listView = new ListView<>(lines);
		listView.setSelectionModel(new NoSelectionModel<>());
		listView.setFocusTraversable(false);
		listView.setCellFactory(cellFactory);
		// AtlantaFX's Cupertino theme sets -fx-cell-size: 3em on every ListView cell (~macOS
		// Finder-list row height) regardless of actual content height - fine for a handful of
		// menu-like rows, but each row here is one source line, so that reads as huge gaps between
		// lines of code. A fixed cell size is the correct override (not CSS padding tweaks): it
		// takes priority over the theme's own -fx-cell-size and is also how ListView expects a
		// uniform-height virtualized list to be sized in the first place.
		listView.setFixedCellSize(20);
		return listView;
	}

	private Callback<ListView<NodeTextBlock[]>, ListCell<NodeTextBlock[]>> createCellFactory() {
		return new Callback<>() {
			@Override
			public ListCell<NodeTextBlock[]> call(ListView<NodeTextBlock[]> param) {
				ListCell<NodeTextBlock[]> cell = new ListCell<>() {
					@Override
					protected void updateItem(NodeTextBlock[] blocks, boolean empty) {
						HBox old = (HBox) getGraphic();
						if (old != null) {
							for (javafx.scene.Node n : old.getChildren()) {
								if (n instanceof TextBlockLabel tbl) {
									tbl.backgroundProperty().unbind();
									tbl.highlightedProperty().unbind();
								}
							}
						}

						super.updateItem(blocks, empty);

						if (empty || null == blocks) {
							setGraphic(null);
						} else {
							HBox box = getCellContent(blocks);
							setGraphic(box);
						}
					}
				};
				if (style != null) {
					cell.getStylesheets().add(style);
				}
				return cell;
			}
		};
	}

	private HBox getCellContent(NodeTextBlock[] blocks) {
		HBox box = new HBox();
		for (NodeTextBlock ntb : blocks) {
			TextBlockLabel l = new TextBlockLabel(ntb.getText());
			if (!ntb.isFirst() && !ntb.isLast()) {
				l.getStyleClass().add("innerBlock");
			} else if (!ntb.isLast()) {
				l.getStyleClass().add("firstBlock");
			} else if (!ntb.isFirst()) {
				l.getStyleClass().add("lastBlock");
			}

			l.setOnMouseEntered(e -> {
				showAssociationInfo(ntb.getAssociation());
				ntb.mouseoverProperty().set(true);
			});
			l.setOnMouseExited(e -> ntb.mouseoverProperty().set(false));

			l.backgroundProperty().set(ntb.backgroundProperty().getValue());
			l.backgroundProperty().bind(ntb.backgroundProperty());

			l.highlightedProperty().set(ntb.highlightedProperty().getValue());
			l.highlightedProperty().bind(ntb.highlightedProperty());

			LilypondSyntaxHighlighter.Style style = ntb.getStyle();
			StringBuilder css = new StringBuilder();
			if (style.color() != null) {
				css.append("-fx-text-fill: ").append(toCssRgb(style.color())).append(";");
			}
			css.append("-fx-font-weight: ").append(style.bold() ? "bold" : "normal").append(";");
			css.append("-fx-font-style: ").append(style.italic() ? "italic" : "normal").append(";");
			l.setStyle(css.toString());

			box.getChildren().add(l);
		}
		return box;
	}

	@Override
	public void showTree(Node node) {
		final Node n = node.getNode(); // in case of a wrapped node
		Node treeRoot = n;
		while (treeRoot.getParent() != null) {
			treeRoot = treeRoot.getParent();
		}

		List<Node> fileNodes = new ArrayList<>();
		collectFileNodes(treeRoot, fileNodes);

		Node selectedFileNode = getPluginNode(n);

		Map<Path, FileView> newFileViews = new LinkedHashMap<>();
		FileView selectedFileView = null;

		for (Node fileNode : fileNodes) {
			PluginArtifactData pad = (PluginArtifactData) fileNode.getArtifact().getData();
			Path fileName = pad.getFileName();

			FileView fv = fileViews.get(fileName);
			if (fv == null) {
				fv = new FileView();
			}
			newFileViews.put(fileName, fv);

			boolean isSelected = fileNode.equals(selectedFileNode);
			fv.showFile(fileNode, isSelected ? n : null);
			if (isSelected) {
				selectedFileView = fv;
			}
		}

		fileViews.clear();
		fileViews.putAll(newFileViews);

		rebuildView(newFileViews, selectedFileView);
	}

	private void rebuildView(Map<Path, FileView> currentFileViews, FileView selectedFileView) {
		if (currentFileViews.size() <= 1) {
			ListView<NodeTextBlock[]> listView = currentFileViews.isEmpty()
					? createListView(FXCollections.observableArrayList())
					: currentFileViews.values().iterator().next().listView;
			splitPane.getItems().set(0, listView);
			return;
		}

		TabPane tabPane = new TabPane();
		Tab tabToSelect = null;
		for (Map.Entry<Path, FileView> entry : currentFileViews.entrySet()) {
			FileView fv = entry.getValue();
			Tab tab = new Tab(entry.getKey().toString(), fv.listView);
			tab.setClosable(false);
			tabPane.getTabs().add(tab);
			if (fv == selectedFileView) {
				tabToSelect = tab;
			}
		}
		if (tabToSelect != null) {
			tabPane.getSelectionModel().select(tabToSelect);
		}
		splitPane.getItems().set(0, tabPane);
	}

	@Override
	public void setShowDetailsPanel(boolean show) {
		boolean currentlyShown = this.splitPane.getItems().contains(this.taInfo);
		if (show == currentlyShown) return;
		if (show) {
			this.splitPane.getItems().add(this.taInfo);
			Platform.runLater(() -> this.splitPane.setDividerPositions(0.95));
		} else {
			this.splitPane.getItems().remove(this.taInfo);
		}
	}

	@Override
	public String getPluginId() {
		return LilypondPlugin.class.getName();
	}

	private Node getPluginNode(Node n) {
		while (n != null && !(n.getArtifact().getData() instanceof PluginArtifactData)) {
			n = n.getParent();
		}
		return n;
	}

	private void collectFileNodes(Node n, List<Node> result) {
		// composition root/container nodes have no artifact of their own
		if (n.getArtifact() != null) {
			ArtifactData d = n.getArtifact().getData();
			if (d instanceof PluginArtifactData pad && getPluginId().equals(pad.getPluginId())) {
				result.add(n);
			}
		}
		for (Node child : n.getChildren()) {
			collectFileNodes(child, result);
		}
	}

	private static String toCssRgb(Color color) {
		return String.format("rgb(%d,%d,%d)",
				(int) Math.round(color.getRed() * 255),
				(int) Math.round(color.getGreen() * 255),
				(int) Math.round(color.getBlue() * 255));
	}

	private void showAssociationInfo(Association a) {
		if (taInfo == null || a == null) return;

		Condition c = a.computeCondition();
		taInfo.setText(a.getId().concat(" (").concat(c.getSimpleModuleRevisionConditionString()).concat(")\n").concat(c.getModuleRevisionConditionString()));
	}

	@Override
	public void setAssociationInfos(Collection<AssociationInfo> associationInfos) {
		fileViews.clear(); // rebuild all file trees on next 'showTree()'

		// remove listeners
		for (Map.Entry<String, AssociationInfo> entry : this.associationInfos.entrySet()) {
			entry.getValue().removePropertyChangeListener(associationListeners.get(entry.getKey()));
		}

		this.associationInfos.clear();
		associationListeners.clear();
		if (associationInfos == null) {
			return;
		}

		for (AssociationInfo ai : associationInfos) {
			this.associationInfos.put(ai.getAssociation().getId(), ai);
		}

		// add listeners
		for (AssociationInfo ai : this.associationInfos.values()) {
			final PropertyChangeListener pcl = getColorPropertyListener();
			ai.addPropertyChangeListener(pcl);
			associationListeners.put(ai.getAssociation().getId(), pcl);
		}
	}

	private PropertyChangeListener getColorPropertyListener() {
		return evt -> {
			if (evt.getPropertyName().equals("color")) {
				AssociationInfo ai = (AssociationInfo) evt.getSource();
				if (!Boolean.TRUE.equals(ai.getPropertyValue("selected"))) {
					return;
				}
				String aId = ai.getAssociation().getId();
				for (FileView fv : fileViews.values()) {
					for (NodeTextBlock[] blocks : fv.codeLines) {
						for (NodeTextBlock ntb : blocks) {
							if (ntb.getAssociation() != null && aId.equals(ntb.getAssociation().getId())) {
								ntb.backgroundColor().set((Color) evt.getNewValue());
							}
						}
					}
					fv.listView.refresh();
				}
			}
		};
	}

	/**
	 * Per-file rendering state: the built lines, the position cache used to scroll to/highlight a
	 * specific node, and whether the (potentially expensive, py4j-backed) parse has completed. Kept
	 * per file so switching tabs, or re-showing a file already seen in this session, doesn't
	 * re-trigger a background parse.
	 */
	private class FileView {

		private final ObservableList<NodeTextBlock[]> codeLines = FXCollections.observableArrayList();
		private final ListView<NodeTextBlock[]> listView = createListView(codeLines);
		private final Map<String, int[]> nodeIdIndexes = new HashMap<>();
		private final List<NodeTextBlock> currentlyHighlighted = new ArrayList<>();
		private Node root;
		private volatile boolean isTreeInitialized = false;

		void showFile(Node fileRoot, Node nodeToHighlight) {
			this.root = fileRoot;

			if (!isTreeInitialized) {
				nodeIdIndexes.clear();
				nodeIdIndexes.put("0", new int[]{0, 0});

				Task<Void> buildTask = new Task<>() {
					@Override
					protected Void call() {
						final ArrayList<NodeTextBlock[]> lines = new ArrayList<>();
						ArrayList<NodeTextBlock> line = new ArrayList<>();
						int[] pos = new int[]{0, 0};
						int idx = 0;
						for (Node cn : root.getChildren()) {
							String id = "0.".concat(String.valueOf(idx));
							nodeIdIndexes.put(id, new int[]{pos[0], pos[1]});
							line = buildCodeLinesRec(cn, id, pos, lines, line);
							idx++;
						}
						NodeTextBlock[] lastLine = new NodeTextBlock[line.size()];
						lines.add(line.toArray(lastLine));

						isTreeInitialized = true;
						Platform.runLater(() -> {
							codeLines.clear();
							codeLines.addAll(lines);
							if (nodeToHighlight != null) {
								highlightTree(nodeToHighlight);
							}
						});
						return null;
					}
				};
				new Thread(buildTask).start();

			} else if (nodeToHighlight != null) {
				highlightTree(nodeToHighlight);
			}
		}

		private void highlightTree(Node node) {
			// clear whatever was highlighted from a previous call -- otherwise every node ever passed
			// to showTree() in this file view would stay highlighted forever, since nothing else ever
			// un-highlights a block.
			for (NodeTextBlock previouslyHighlighted : currentlyHighlighted) {
				previouslyHighlighted.setHighlighted(false);
			}
			currentlyHighlighted.clear();

			String curId = calculateNodeId(node);
			int[] pos = nodeIdIndexes.get(curId);
			listView.scrollTo(pos[0]);

			NodeTextBlock ntb;
			do {
				NodeTextBlock[] line = codeLines.get(pos[0]);
				if (line.length > 0) {
					ntb = line[pos[1]];
					ntb.setHighlighted(true);
					currentlyHighlighted.add(ntb);
					if (pos[1] < line.length - 1) {
						pos[1]++;
					} else {
						pos[0]++;
						pos[1] = 0;
					}
				} else {
					ntb = null;
				}
			} while (ntb != null && !ntb.isLast());
		}

		private String calculateNodeId(Node node) {
			StringBuilder sb = new StringBuilder();
			while (node != null && node != root) {
				List<? extends Node> children = node.getParent().getChildren();
				int i = 0;
				while (children.get(i) != node.getNode()) { // compare by reference
					i++;
				}
				sb.insert(0, i)
						.insert(0, ".");
				node = node.getParent();
			}
			sb.insert(0, "0");
			return sb.toString();
		}

		private ArrayList<NodeTextBlock> buildCodeLinesRec(Node n, String nodeId, int[] pos, Collection<NodeTextBlock[]> lines, ArrayList<NodeTextBlock> line) {
			ArtifactData d = n.getArtifact().getData();
			if (d instanceof BaseContextArtifactData) {
				List<? extends Node> children = n.getChildren();
				for (int i = 0; i < children.size(); i++) {
					String id = nodeId.concat(".").concat(String.valueOf(i));
					nodeIdIndexes.put(id, new int[]{pos[0], pos[1]});
					line = buildCodeLinesRec(children.get(i), id, pos, lines, line);
				}

			} else if (d instanceof DefaultTokenArtifactData) {
				Association ass = n.getArtifact().getContainingNode() != null
						? n.getArtifact().getContainingNode().getContainingAssociation()
						: null;
				Color bgCol = Color.WHITE;
				if (ass != null) {
					String aiId = ass.getId();
					AssociationInfo ai = associationInfos.get(aiId);
					if (ai != null && Boolean.TRUE.equals(ai.getPropertyValue("selected"))) {
						Object val = ai.getPropertyValue("color");
						if (val instanceof Color col && !col.equals(Color.TRANSPARENT)) {
							bgCol = col;
						}
					}
				}

				NodeTextBlock ntb = new NodeTextBlock(n, bgCol);

				line.add(ntb);
				pos[1]++;

				if (ntb.numLines() > 1) {
					NodeTextBlock[] l = new NodeTextBlock[line.size()];
					lines.add(line.toArray(l));
					pos[0]++;

					for (int i = 1; i < ntb.numLines(); i++) {
						if (i < ntb.numLines() - 1) {
							lines.add(new NodeTextBlock[]{ntb.getGroup().get(i)});
							pos[0]++;
						} else {
							line = new ArrayList<>();
							line.add(ntb.getGroup().get(i));
							pos[1] = 1;
						}
					}
				}
			}

			return line;
		}
	}

	private static class NoSelectionModel<T> extends MultipleSelectionModel<T> {

		@Override
		public ObservableList<Integer> getSelectedIndices() {
			return FXCollections.emptyObservableList();
		}

		@Override
		public ObservableList<T> getSelectedItems() {
			return FXCollections.emptyObservableList();
		}

		@Override
		public void selectIndices(int index, int... indices) {
		}

		@Override
		public void selectAll() {
		}

		@Override
		public void clearAndSelect(int index) {
		}

		@Override
		public void select(int index) {
		}

		@Override
		public void select(T obj) {
		}

		@Override
		public void clearSelection(int index) {
		}

		@Override
		public void clearSelection() {
		}

		@Override
		public boolean isSelected(int index) {
			return false;
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public void selectPrevious() {
		}

		@Override
		public void selectNext() {
		}

		@Override
		public void selectFirst() {
		}

		@Override
		public void selectLast() {
		}
	}
}
