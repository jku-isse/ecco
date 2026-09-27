package at.jku.isse.ecco.adapter.view;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Association preview for adapters whose trees don't hold their source text line by line: the
 * adapter renders a file ({@link #render}) the way its writer would, recording which node produced
 * which text, and every character is colored like the association of the node it belongs to.
 * <p>
 * Like the other viewers, a tree spanning several files of this adapter shows each as its own tab,
 * opened at the selected node, whose text is highlighted.
 */
public abstract class SourceSpanViewer extends BorderPane implements AssociationInfoArtifactViewer {

	private final Map<String, AssociationInfo> associationInfos = new HashMap<>();
	private final Map<String, PropertyChangeListener> associationListeners = new HashMap<>();
	private final List<ListView<List<RenderedSource.Segment>>> listViews = new ArrayList<>();

	/**
	 * The file's text as the adapter writes it, with the span each node produced.
	 *
	 * @param fileNode the node of a file of this adapter (its artifact data is a {@link PluginArtifactData})
	 * @throws Exception if the file cannot be rendered - shown in its place
	 */
	protected abstract RenderedSource render(Node fileNode) throws Exception;

	@Override
	public void showTree(Node node) {
		Node root = node;
		while (root.getParent() != null)
			root = root.getParent();
		List<Node> fileNodes = new ArrayList<>();
		this.collectFileNodes(root, fileNodes);
		Node selectedFileNode = this.fileNodeOf(node);

		this.listViews.clear();
		TabPane tabPane = new TabPane();
		ListView<List<RenderedSource.Segment>> selectedView = null;
		for (Node fileNode : fileNodes) {
			List<List<RenderedSource.Segment>> lines;
			int scrollTo = -1;
			try {
				RenderedSource source = this.render(fileNode);
				Node selected = fileNode == selectedFileNode ? node : null;
				lines = source.lines(selected);
				scrollTo = selected == null ? -1 : source.lineOf(selected);
			} catch (Exception e) {
				lines = new ArrayList<>();
				String message = e.getMessage() != null ? e.getMessage() : e.toString();
				for (String line : ("Cannot show this file: " + message).split("\n"))
					lines.add(List.of(new RenderedSource.Segment(line, null, false)));
			}
			ListView<List<RenderedSource.Segment>> listView = this.createListView(lines);
			this.listViews.add(listView);
			if (scrollTo >= 0) {
				int index = scrollTo;
				Platform.runLater(() -> listView.scrollTo(index));
			}
			Tab tab = new Tab(((PluginArtifactData) fileNode.getArtifact().getData()).getPath().toString(), listView);
			tab.setClosable(false);
			tabPane.getTabs().add(tab);
			if (fileNode == selectedFileNode) {
				tabPane.getSelectionModel().select(tab);
				selectedView = listView;
			}
		}

		if (this.listViews.size() == 1)
			this.setCenter(this.listViews.get(0));
		else if (this.listViews.isEmpty())
			this.setCenter(this.createListView(new ArrayList<>()));
		else
			this.setCenter(tabPane);
	}

	private ListView<List<RenderedSource.Segment>> createListView(List<List<RenderedSource.Segment>> lines) {
		ListView<List<RenderedSource.Segment>> listView = new ListView<>(FXCollections.observableArrayList(lines));
		listView.setFocusTraversable(false);
		listView.setCellFactory(view -> new ListCell<>() {
			@Override
			protected void updateItem(List<RenderedSource.Segment> segments, boolean empty) {
				super.updateItem(segments, empty);
				this.setText(null);
				if (empty || segments == null) {
					this.setGraphic(null);
					return;
				}
				HBox row = new HBox();
				row.setAlignment(Pos.BASELINE_LEFT);
				for (RenderedSource.Segment segment : segments) {
					// tabs don't render in a Label - as elsewhere in the viewers, 4 spaces
					Label label = new Label(segment.text().replace("\t", "    ").replace("\r", ""));
					label.setStyle("-fx-font-family: monospace;");
					label.setMinWidth(Label.USE_PREF_SIZE);
					Color color = segment.selected() ? Color.YELLOW : SourceSpanViewer.this.colorOf(segment.node());
					if (color != null)
						label.setBackground(new Background(new BackgroundFill(color, null, null)));
					row.getChildren().add(label);
				}
				this.setGraphic(row);
			}
		});
		// same as the other code viewers: the theme's cell size reads as gaps between lines of code
		listView.setFixedCellSize(20);
		return listView;
	}

	/** The color of the selected association {@code node} belongs to, or null. */
	private Color colorOf(Node node) {
		if (node == null || node.getArtifact() == null || node.getArtifact().getContainingNode() == null)
			return null;
		Association association = node.getArtifact().getContainingNode().getContainingAssociation();
		if (association == null)
			return null;
		AssociationInfo info = this.associationInfos.get(association.getId());
		if (info == null || !Boolean.TRUE.equals(info.getPropertyValue("selected")))
			return null;
		return info.getPropertyValue("color") instanceof Color color && !Color.TRANSPARENT.equals(color) ? color : null;
	}

	private void collectFileNodes(Node node, List<Node> result) {
		if (this.isFileNode(node)) {
			result.add(node);
			return;
		}
		for (Node child : node.getChildren())
			this.collectFileNodes(child, result);
	}

	private Node fileNodeOf(Node node) {
		for (Node current = node; current != null; current = current.getParent())
			if (this.isFileNode(current))
				return current;
		return null;
	}

	private boolean isFileNode(Node node) {
		if (node.getArtifact() == null)
			return false;
		ArtifactData data = node.getArtifact().getData();
		return data instanceof PluginArtifactData pluginData && this.getPluginId().equals(pluginData.getPluginId());
	}

	@Override
	public void setAssociationInfos(Collection<AssociationInfo> associationInfos) {
		for (Map.Entry<String, AssociationInfo> entry : this.associationInfos.entrySet())
			entry.getValue().removePropertyChangeListener(this.associationListeners.get(entry.getKey()));
		this.associationInfos.clear();
		this.associationListeners.clear();
		if (associationInfos == null)
			return;
		for (AssociationInfo info : associationInfos) {
			String id = info.getAssociation().getId();
			this.associationInfos.put(id, info);
			PropertyChangeListener listener = event -> {
				if ("color".equals(event.getPropertyName()) || "selected".equals(event.getPropertyName()))
					Platform.runLater(() -> this.listViews.forEach(ListView::refresh));
			};
			info.addPropertyChangeListener(listener);
			this.associationListeners.put(id, listener);
		}
	}
}
