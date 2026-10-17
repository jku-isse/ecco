package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.view.AssociationColors;
import at.jku.isse.ecco.adapter.view.FileTabs;
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
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
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

	private final AssociationColors colors = new AssociationColors(this::recolor);
	private final Map<Path, FileView> fileViews = new LinkedHashMap<>();
	private boolean highlightSelection = true;

	public TextViewer() {
		this.setCenter(createListView(FXCollections.observableArrayList()));
	}

	@Override
	public void showTree(Node node) {
		List<Node> fileNodes = FileTabs.files(node, getPluginId());
		Node selectedFileNode = FileTabs.fileOf(node, getPluginId());

		Map<Path, FileView> newFileViews = new LinkedHashMap<>();
		FileView selectedFileView = null;

		for (Node fileNode : fileNodes) {
			PluginArtifactData pad = (PluginArtifactData) fileNode.getArtifact().getData();
			// by path: two files of one name in different folders shared a tab, only the last shown
			Path fileName = pad.getPath();

			FileView fv = fileViews.get(fileName);
			if (fv == null) {
				fv = new FileView(fileName);
			}
			newFileViews.put(fileName, fv);

			// only the file shown now, the others once their tab is selected: every file was, on
			// every click - for a commit with many files the whole commit
			if (fileNode.equals(selectedFileNode)) {
				fv.showFile(fileNode, node);
				selectedFileView = fv;
			} else {
				fv.showLater(fileNode);
			}
		}

		fileViews.clear();
		fileViews.putAll(newFileViews);

		// a view's lines are built once its tab is shown
		this.setCenter(FileTabs.show(new ArrayList<>(newFileViews.values()), selectedFileView, fv -> fv.path.toString(),
				fv -> fv.shown().listView, () -> createListView(FXCollections.observableArrayList())));
	}

	@Override
	public String getPluginId() {
		return TextPlugin.class.getName();
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
	public void setHighlightSelection(boolean highlight) {
		this.highlightSelection = highlight;
	}

	@Override
	public void setAssociationInfos(Collection<AssociationInfo> associationInfos) {
		fileViews.clear(); // rebuild all file views on next showTree()

		colors.set(associationInfos);
	}

	/** Colours the lines of association [id] [color]: its colour changed. */
	private void recolor(String id, Color color) {
		for (FileView fv : fileViews.values()) {
			for (LineRow row : fv.lines) {
				if (row.getAssociation() != null && id.equals(row.getAssociation().getId())) {
					row.backgroundColor().set(color);
				}
			}
			fv.listView.refresh();
		}
	}

	/** Per-file rendering state: the built lines, kept per file so switching tabs, or re-showing a
	 * file already seen in this session, doesn't rebuild from scratch. */
	private class FileView {

		private final Path path;

		private final ObservableList<LineRow> lines = FXCollections.observableArrayList();
		private final ListView<LineRow> listView = createListView(lines);

		FileView(Path path) {
			this.path = path;
		}

		/** The file to show once this view is shown - see {@link #showLater}. */
		private Node pending;

		/** Remembers [fileRoot] to show when this view is shown ({@link #shown}) - not built now. */
		void showLater(Node fileRoot) {
			this.pending = fileRoot;
		}

		/** Shows the file remembered by {@link #showLater}, if any; returns this. */
		FileView shown() {
			if (this.pending != null)
				this.showFile(this.pending, null);
			return this;
		}

		void showFile(Node fileRoot, Node nodeToHighlight) {
			this.pending = null;
			lines.clear();
			for (Node child : fileRoot.getChildren()) {
				lines.add(new LineRow(child, colors.colorFor(child)));
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
				row.setHighlighted(match && highlightSelection);
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
			this.association = AssociationColors.associationOf(node);
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
