package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;

import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows a text file's lines, highlighting each line with its owning association's selection color
 * -- the whole-line-granularity counterpart to {@code LilypondCodeViewer}/{@code JavaCodeViewer}/
 * {@code CCodeViewer}'s per-token coloring. Text artifacts are already line-granular (one
 * {@code LineArtifactData} node per line), so there's no tokenizing to do -- each line node's own
 * containing association is looked up directly, the same {@code Artifact#getContainingNode()} ->
 * {@code Node#getContainingAssociation()} path the other three viewers already use.
 *
 * <p>A commit (or composed view) can span multiple text files, so {@link #showTree(Node)} shows
 * each as its own tab, defaulting to the one actually selected (mirrors the other
 * {@code AssociationInfoArtifactViewer}s' multi-file handling); if only one file is present, the
 * tab chrome is skipped. Selecting a specific node (e.g. from the reorder dialog, or navigating
 * from the Artifacts tree) scrolls to and highlights that line in yellow.
 */
public class TextViewer extends BorderPane implements AssociationInfoArtifactViewer {

	private final Map<String, AssociationInfo> associationInfos = new HashMap<>();
	private final Map<String, PropertyChangeListener> associationListeners = new HashMap<>();
	private final Map<Path, FileView> fileViews = new LinkedHashMap<>();

	public TextViewer() {
		this.setCenter(createListView(FXCollections.observableArrayList()));
	}

	@Override
	public void showTree(Node node) {
		Node treeRoot = node;
		while (treeRoot.getParent() != null) {
			treeRoot = treeRoot.getParent();
		}

		List<Node> fileNodes = new ArrayList<>();
		collectFileNodes(treeRoot, fileNodes);

		Node selectedFileNode = getPluginNode(node);

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
			fv.showFile(fileNode, isSelected ? node : null);
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
			ListView<LineRow> listView = currentFileViews.isEmpty()
					? createListView(FXCollections.observableArrayList())
					: currentFileViews.values().iterator().next().listView;
			this.setCenter(listView);
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
		this.setCenter(tabPane);
	}

	@Override
	public String getPluginId() {
		return TextPlugin.class.getName();
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

	private ListView<LineRow> createListView(ObservableList<LineRow> lines) {
		ListView<LineRow> listView = new ListView<>(lines);
		listView.setFocusTraversable(false);
		listView.setCellFactory(lv -> new ListCell<>() {
			{
				setMaxWidth(Double.MAX_VALUE);
			}

			@Override
			protected void updateItem(LineRow row, boolean empty) {
				LineRow old = getItem();
				if (old != null) {
					backgroundProperty().unbind();
				}
				super.updateItem(row, empty);
				if (empty || row == null) {
					setText(null);
					setBackground(null);
				} else {
					setText(row.getText());
					backgroundProperty().bind(row.backgroundProperty());
				}
			}
		});
		// same rationale as LilypondCodeViewer/JavaCodeViewer: AtlantaFX's Cupertino theme sets a
		// ~macOS-Finder-list -fx-cell-size on every ListView, which reads as huge gaps between lines
		// of text otherwise.
		listView.setFixedCellSize(20);
		return listView;
	}

	@Override
	public void setAssociationInfos(Collection<AssociationInfo> associationInfos) {
		fileViews.clear(); // rebuild all file views on next showTree()

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
			PropertyChangeListener pcl = getColorPropertyListener();
			ai.addPropertyChangeListener(pcl);
			associationListeners.put(ai.getAssociation().getId(), pcl);
		}
	}

	private PropertyChangeListener getColorPropertyListener() {
		return evt -> {
			if (!"color".equals(evt.getPropertyName())) return;
			AssociationInfo ai = (AssociationInfo) evt.getSource();
			if (!Boolean.TRUE.equals(ai.getPropertyValue("selected"))) return;
			String aId = ai.getAssociation().getId();
			for (FileView fv : fileViews.values()) {
				for (LineRow row : fv.lines) {
					if (row.getAssociation() != null && aId.equals(row.getAssociation().getId())) {
						row.backgroundColor().set((Color) evt.getNewValue());
					}
				}
				fv.listView.refresh();
			}
		};
	}

	private Color colorFor(Node lineNode) {
		Association association = containingAssociation(lineNode);
		if (association != null) {
			AssociationInfo ai = associationInfos.get(association.getId());
			if (ai != null && Boolean.TRUE.equals(ai.getPropertyValue("selected"))) {
				Object val = ai.getPropertyValue("color");
				if (val instanceof Color col && !col.equals(Color.TRANSPARENT)) {
					return col;
				}
			}
		}
		return Color.WHITE;
	}

	private static Association containingAssociation(Node node) {
		return node.getArtifact() != null && node.getArtifact().getContainingNode() != null
				? node.getArtifact().getContainingNode().getContainingAssociation() : null;
	}

	/** Per-file rendering state: the built lines, kept per file so switching tabs, or re-showing a
	 * file already seen in this session, doesn't rebuild from scratch. */
	private class FileView {

		private final ObservableList<LineRow> lines = FXCollections.observableArrayList();
		private final ListView<LineRow> listView = createListView(lines);

		void showFile(Node fileRoot, Node nodeToHighlight) {
			lines.clear();
			for (Node child : fileRoot.getChildren()) {
				lines.add(new LineRow(child, colorFor(child)));
			}
			if (nodeToHighlight != null) {
				highlight(nodeToHighlight);
			}
		}

		void highlight(Node node) {
			int idx = -1;
			for (int i = 0; i < lines.size(); i++) {
				LineRow row = lines.get(i);
				boolean match = row.getNode().equals(node);
				row.setHighlighted(match);
				if (match) idx = i;
			}
			if (idx >= 0) {
				listView.scrollTo(idx);
			}
		}
	}

	private static class LineRow {
		private final Node node;
		private final Association association;
		private final String text;
		private final BooleanProperty highlighted = new SimpleBooleanProperty(false);
		private final ObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>();
		private final ObjectProperty<Background> background = new SimpleObjectProperty<>();

		LineRow(Node node, Color initialColor) {
			this.node = node;
			this.association = containingAssociation(node);
			this.text = (node.getArtifact() != null && node.getArtifact().getData() instanceof LineArtifactData lineArtifactData)
					? lineArtifactData.getLine() : String.valueOf(node.getArtifact());

			highlighted.addListener((o, oldVal, newVal) -> updateBackground());
			backgroundColor.addListener((o, oldVal, newVal) -> updateBackground());
			backgroundColor.set(initialColor);
		}

		private void updateBackground() {
			Color color = Boolean.TRUE.equals(highlighted.get()) ? Color.YELLOW : backgroundColor.get();
			if (color == null || Color.TRANSPARENT.equals(color)) {
				color = Color.WHITE;
			}
			background.set(new Background(new BackgroundFill(color, null, null)));
		}

		Node getNode() {
			return node;
		}

		Association getAssociation() {
			return association;
		}

		String getText() {
			return text;
		}

		void setHighlighted(boolean flag) {
			highlighted.set(flag);
		}

		ObjectProperty<Color> backgroundColor() {
			return backgroundColor;
		}

		ReadOnlyObjectProperty<Background> backgroundProperty() {
			return background;
		}
	}
}
