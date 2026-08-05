package at.jku.isse.ecco.adapter.c.view;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.c.CPlugin;
import at.jku.isse.ecco.adapter.c.data.FunctionArtifactData;
import at.jku.isse.ecco.adapter.c.data.LineArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Callback;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows a reconstructed view of a C artifact tree, mirroring the Java adapter's JavaCodeViewer.
 * Unlike Java's model, a {@code LineArtifactData}'s stored text is already a full original source
 * line including its leading whitespace, and {@code FunctionArtifactData} is a pure grouping node
 * whose real opening-brace line is already one of its children - so lines are rendered exactly as
 * stored, with no synthetic indent or bracket lines.
 * <p>
 * A commit (or any other composed view) can span multiple C files, so {@link #showTree(Node)}
 * walks up to the true root of whatever tree was passed in, then finds every distinct C file
 * reachable from there (each is its own {@link PluginArtifactData} node) and shows each as its own
 * tab, defaulting to the tab/line containing the node that was actually selected. If only one file
 * is present, the tab chrome is skipped and the code is shown directly, matching the single-file
 * Artifacts panel case.
 */
public class CCodeViewer extends BorderPane implements AssociationInfoArtifactViewer {

	private final HashMap<String, AssociationInfo> associationInfos = new HashMap<>();
	private final HashMap<String, PropertyChangeListener> associationListeners = new HashMap<>();
	private final Map<Node, ObservableList<CCodeLine>> linesByFile = new LinkedHashMap<>();
	private final Callback<ListView<CCodeLine>, ListCell<CCodeLine>> cellFactory = createCellFactory();
	private final SplitPane splitPane;
	private final TextArea taInfo;

	public CCodeViewer() {
		taInfo = new TextArea();
		taInfo.setMinHeight(60);
		taInfo.setWrapText(true);
		SplitPane.setResizableWithParent(taInfo, false);

		splitPane = new SplitPane(createListView(FXCollections.observableArrayList()), taInfo);
		splitPane.setOrientation(Orientation.VERTICAL);

		this.setCenter(splitPane);

		Platform.runLater(() -> splitPane.setDividerPositions(0.95));
	}

	@Override
	public void showTree(Node node) {
		final Node n = node.getNode();
		Node root = n;
		while (root.getParent() != null) {
			root = root.getParent();
		}

		List<Node> fileNodes = new ArrayList<>();
		collectFileNodes(root, fileNodes);

		linesByFile.clear();

		// found by walking UP from the clicked node, rather than checking indexByNode below,
		// since that only tracks nodes that produced a rendered line - a FunctionArtifactData
		// node (a pure grouping node, see renderNode) or the file node itself never would, and
		// would otherwise fail to select any tab at all
		Node selectedFileNode = findContainingFileNode(n);
		Integer selectedIndex = null;

		for (Node fileNode : fileNodes) {
			List<CCodeLine> built = new ArrayList<>();
			Map<Node, Integer> indexByNode = new HashMap<>();
			for (Node child : fileNode.getChildren()) {
				renderNode(child, built, indexByNode);
			}

			ObservableList<CCodeLine> lines = FXCollections.observableArrayList(built);
			linesByFile.put(fileNode, lines);

			if (fileNode.equals(selectedFileNode)) {
				selectedIndex = indexByNode.get(n);
			}
		}

		rebuildView(selectedFileNode, selectedIndex);
	}

	@Override
	public String getPluginId() {
		return CPlugin.class.getName();
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

	private Node findContainingFileNode(Node n) {
		for (Node cur = n; cur != null; cur = cur.getParent()) {
			if (cur.getArtifact() != null) {
				ArtifactData d = cur.getArtifact().getData();
				if (d instanceof PluginArtifactData pad && getPluginId().equals(pad.getPluginId())) {
					return cur;
				}
			}
		}
		return null;
	}

	private void rebuildView(Node selectedFileNode, Integer selectedIndex) {
		if (linesByFile.size() <= 1) {
			ObservableList<CCodeLine> lines = linesByFile.isEmpty()
					? FXCollections.observableArrayList()
					: linesByFile.values().iterator().next();
			ListView<CCodeLine> listView = createListView(lines);
			splitPane.getItems().set(0, listView);
			scrollToLine(listView, selectedIndex);
			return;
		}

		TabPane tabPane = new TabPane();
		Tab tabToSelect = null;
		for (Map.Entry<Node, ObservableList<CCodeLine>> entry : linesByFile.entrySet()) {
			Node fileNode = entry.getKey();
			ListView<CCodeLine> listView = createListView(entry.getValue());

			Tab tab = new Tab(fileLabel(fileNode), listView);
			tab.setClosable(false);
			tabPane.getTabs().add(tab);

			if (fileNode.equals(selectedFileNode)) {
				tabToSelect = tab;
				scrollToLine(listView, selectedIndex);
			}
		}

		if (tabToSelect != null) {
			tabPane.getSelectionModel().select(tabToSelect);
		}

		splitPane.getItems().set(0, tabPane);
	}

	private void scrollToLine(ListView<CCodeLine> listView, Integer index) {
		if (index == null) {
			return;
		}
		Platform.runLater(() -> {
			listView.scrollTo(index);
			listView.getSelectionModel().clearAndSelect(index);
		});
	}

	private String fileLabel(Node fileNode) {
		ArtifactData d = fileNode.getArtifact().getData();
		if (d instanceof PluginArtifactData pad) {
			return pad.getPath().toString();
		}
		return "?";
	}

	private ListView<CCodeLine> createListView(ObservableList<CCodeLine> lines) {
		ListView<CCodeLine> listView = new ListView<>(lines);
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

	private Callback<ListView<CCodeLine>, ListCell<CCodeLine>> createCellFactory() {
		return new Callback<>() {
			@Override
			public ListCell<CCodeLine> call(ListView<CCodeLine> param) {
				return new ListCell<>() {
					@Override
					protected void updateItem(CCodeLine line, boolean empty) {
						super.updateItem(line, empty);
						if (empty || line == null) {
							setText(null);
							setGraphic(null);
							backgroundProperty().unbind();
						} else {
							TextFlow flow = new TextFlow();
							flow.setOnMouseEntered(e -> showAssociationInfo(line.getAssociation()));

							for (CSyntaxHighlighter.Token token : CSyntaxHighlighter.tokenize(line.getText())) {
								Text text = new Text(token.text());
								CSyntaxHighlighter.Style style = token.style();
								if (style.color() != null) {
									text.setFill(style.color());
								}
								StringBuilder css = new StringBuilder();
								css.append("-fx-font-weight: ").append(style.bold() ? "bold" : "normal").append(";");
								css.append("-fx-font-style: ").append(style.italic() ? "italic" : "normal").append(";");
								text.setStyle(css.toString());
								flow.getChildren().add(text);
							}

							setGraphic(flow);
							backgroundProperty().bind(line.backgroundProperty());
						}
					}
				};
			}
		};
	}

	private void renderNode(Node n, List<CCodeLine> lines, Map<Node, Integer> indexByNode) {
		ArtifactData d = n.getArtifact().getData();
		Association association = n.getArtifact().getContainingNode() != null
				? n.getArtifact().getContainingNode().getContainingAssociation()
				: null;

		if (d instanceof FunctionArtifactData) {
			// a pure grouping node - its real opening-brace line is already the first child, but
			// still record where it starts so selecting the function node itself scrolls there
			indexByNode.put(n, lines.size());
			for (Node child : n.getChildren()) {
				renderNode(child, lines, indexByNode);
			}

		} else if (d instanceof LineArtifactData lineData) {
			addLine(n, association, lineData.getLine(), lines, indexByNode);
			for (Node child : n.getChildren()) {
				renderNode(child, lines, indexByNode);
			}

		} else {
			addLine(n, association, String.valueOf(d), lines, indexByNode);
			for (Node child : n.getChildren()) {
				renderNode(child, lines, indexByNode);
			}
		}
	}

	private void addLine(Node n, Association association, String text, List<CCodeLine> lines, Map<Node, Integer> indexByNode) {
		Color bgCol = Color.WHITE;
		if (association != null) {
			String aiId = association.getId();
			AssociationInfo ai = associationInfos.get(aiId);
			if (ai != null && Boolean.TRUE.equals(ai.getPropertyValue("selected"))) {
				Object val = ai.getPropertyValue("color");
				if (val instanceof Color col && !col.equals(Color.TRANSPARENT)) {
					bgCol = col;
				}
			}
		}

		CCodeLine line = new CCodeLine(n, association, text, 0);
		line.backgroundColor().set(bgCol);
		if (n != null) {
			indexByNode.put(n, lines.size());
		}
		lines.add(line);
	}

	private void showAssociationInfo(Association a) {
		if (taInfo == null || a == null) {
			return;
		}

		Condition c = a.computeCondition();
		taInfo.setText(a.getId().concat(" (").concat(c.getSimpleModuleRevisionConditionString()).concat(")\n").concat(c.getModuleRevisionConditionString()));
	}

	@Override
	public void setAssociationInfos(Collection<AssociationInfo> associationInfos) {
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
				for (ObservableList<CCodeLine> lines : linesByFile.values()) {
					for (CCodeLine line : lines) {
						if (line.getAssociation() != null && aId.equals(line.getAssociation().getId())) {
							line.backgroundColor().set((Color) evt.getNewValue());
						}
					}
				}
			}
		};
	}
}
