package at.jku.isse.ecco.adapter.markdown.view;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.markdown.MarkdownPlugin;
import at.jku.isse.ecco.adapter.markdown.data.BlockQuoteArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.BulletListArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.CodeBlockArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.HtmlBlockArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.LineArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.OrderedListArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.SectionArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.TableRowArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.ThematicBreakArtifactData;
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
 * Shows a Markdown file's lines, styled by the block type each line belongs to (bold headings,
 * monospace code/tables, italic blockquotes, indented list items) and highlighted with each line's
 * owning association's selection color - the Markdown-specific counterpart to {@code TextViewer},
 * which it's structurally based on (same whole-line granularity, same multi-file tab handling, same
 * association-color wiring). Unlike {@code TextViewer}, a Markdown file's tree is nested (sections
 * contain headings/paragraphs/lists, lists contain items, tables contain rows), so lines are
 * collected via a recursive walk ({@link #collectLines}) that dispatches on each ancestor's
 * {@code ArtifactData} type - mirroring {@code MarkdownTreeBuilder}'s own build-time dispatch, just in
 * reverse. Rendering is always the raw, verbatim source line (including markdown syntax characters
 * like {@code #}/{@code -}/{@code `}/{@code >}/{@code |}) - a source view, not a rendered preview,
 * matching every other adapter's viewer.
 */
public class MarkdownViewer extends BorderPane implements AssociationInfoArtifactViewer {

	private final Map<String, AssociationInfo> associationInfos = new HashMap<>();
	private final Map<String, PropertyChangeListener> associationListeners = new HashMap<>();
	private final Map<Path, FileView> fileViews = new LinkedHashMap<>();

	public MarkdownViewer() {
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
			ListView<MarkdownLineRow> listView = currentFileViews.isEmpty()
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
		return MarkdownPlugin.class.getName();
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

	/** Recursively collects one {@link MarkdownLineRow} per {@link LineArtifactData} leaf beneath
	 * {@code node}, threading a {@link RenderContext} that accumulates style hints from each
	 * container ancestor's {@code ArtifactData} type - mirrors {@code MarkdownTreeBuilder.translateBlock}'s
	 * build-time dispatch, in reverse. Static (association lookup passed explicitly) so it's testable
	 * without constructing a {@code MarkdownViewer} (a JavaFX {@code Control}) at all. */
	static void collectLines(Node node, RenderContext context, Map<String, AssociationInfo> associationInfos, List<MarkdownLineRow> out) {
		if (node.getArtifact() == null) {
			for (Node child : node.getChildren()) {
				collectLines(child, context, associationInfos, out);
			}
			return;
		}

		ArtifactData data = node.getArtifact().getData();

		if (data instanceof LineArtifactData) {
			out.add(new MarkdownLineRow(node, context, colorFor(node, associationInfos)));
			return;
		}

		if (data instanceof SectionArtifactData) {
			// MarkdownTreeBuilder adds the heading's own source line(s) as sectionNode's leading
			// LineArtifactData children (see translator's comment there), before any gap-fill or
			// nested content; everything else is this section's actual content, styled plainly (a
			// nested H2 section still gets its own HEADING styling for its own heading line, via the
			// recursive call below).
			RenderContext headingContext = context.withKind(LineKind.HEADING);
			boolean inLeadingHeadingRun = true;
			for (Node child : node.getChildren()) {
				boolean isLine = child.getArtifact() != null && child.getArtifact().getData() instanceof LineArtifactData;
				inLeadingHeadingRun &= isLine;
				collectLines(child, inLeadingHeadingRun ? headingContext : context, associationInfos, out);
			}
			return;
		}

		RenderContext childContext = context;
		if (data instanceof CodeBlockArtifactData || data instanceof HtmlBlockArtifactData) {
			childContext = context.withKind(LineKind.CODE);
		} else if (data instanceof BlockQuoteArtifactData) {
			childContext = context.withBlockquote();
		} else if (data instanceof BulletListArtifactData || data instanceof OrderedListArtifactData) {
			childContext = context.withDeeperList();
		} else if (data instanceof TableRowArtifactData tableRow) {
			childContext = context.withKind(tableRow.isHeader() ? LineKind.TABLE_HEADER_ROW : LineKind.TABLE_ROW);
		} else if (data instanceof ThematicBreakArtifactData) {
			childContext = context.withKind(LineKind.THEMATIC_BREAK);
		}
		for (Node child : node.getChildren()) {
			collectLines(child, childContext, associationInfos, out);
		}
	}

	private ListView<MarkdownLineRow> createListView(ObservableList<MarkdownLineRow> lines) {
		ListView<MarkdownLineRow> listView = new ListView<>(lines);
		listView.setFocusTraversable(false);
		listView.setCellFactory(lv -> new ListCell<>() {
			{
				setMaxWidth(Double.MAX_VALUE);
			}

			@Override
			protected void updateItem(MarkdownLineRow row, boolean empty) {
				MarkdownLineRow old = getItem();
				if (old != null) {
					backgroundProperty().unbind();
				}
				super.updateItem(row, empty);
				if (empty || row == null) {
					setText(null);
					setBackground(null);
					setStyle(null);
				} else {
					setText(row.getText());
					backgroundProperty().bind(row.backgroundProperty());
					setStyle(row.getStyle());
				}
			}
		});
		// same rationale as TextViewer/LilypondCodeViewer/JavaCodeViewer: AtlantaFX's Cupertino theme
		// sets a ~macOS-Finder-list -fx-cell-size on every ListView otherwise.
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
				for (MarkdownLineRow row : fv.lines) {
					if (row.getAssociation() != null && aId.equals(row.getAssociation().getId())) {
						row.backgroundColor().set((Color) evt.getNewValue());
					}
				}
				fv.listView.refresh();
			}
		};
	}

	private static Color colorFor(Node lineNode, Map<String, AssociationInfo> associationInfos) {
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

	enum LineKind {
		PLAIN, HEADING, CODE, THEMATIC_BREAK, TABLE_HEADER_ROW, TABLE_ROW
	}

	/** Immutable style state accumulated while walking down from a file's root to a given line -
	 * kind reverts to {@link LineKind#PLAIN} below the node that set it (e.g. a section's kind is
	 * only ever {@code HEADING} for its own heading line, not its nested content), while
	 * {@code listDepth}/{@code inBlockquote} persist for every descendant. Package-private (rather
	 * than private) so {@link #collectLines}'s classification can be unit-tested directly. */
	static final class RenderContext {
		static final RenderContext ROOT = new RenderContext(LineKind.PLAIN, 0, false);

		final LineKind kind;
		final int listDepth;
		final boolean inBlockquote;

		private RenderContext(LineKind kind, int listDepth, boolean inBlockquote) {
			this.kind = kind;
			this.listDepth = listDepth;
			this.inBlockquote = inBlockquote;
		}

		RenderContext withKind(LineKind newKind) {
			return new RenderContext(newKind, this.listDepth, this.inBlockquote);
		}

		RenderContext withBlockquote() {
			return new RenderContext(LineKind.PLAIN, this.listDepth, true);
		}

		RenderContext withDeeperList() {
			return new RenderContext(LineKind.PLAIN, this.listDepth + 1, this.inBlockquote);
		}
	}

	/** Per-file rendering state: the built lines, kept per file so switching tabs, or re-showing a
	 * file already seen in this session, doesn't rebuild from scratch. */
	private class FileView {

		private final ObservableList<MarkdownLineRow> lines = FXCollections.observableArrayList();
		private final ListView<MarkdownLineRow> listView = createListView(lines);

		void showFile(Node fileRoot, Node nodeToHighlight) {
			List<MarkdownLineRow> collected = new ArrayList<>();
			collectLines(fileRoot, RenderContext.ROOT, associationInfos, collected);
			lines.setAll(collected);
			if (nodeToHighlight != null) {
				highlight(nodeToHighlight);
			}
		}

		void highlight(Node node) {
			int idx = -1;
			for (int i = 0; i < lines.size(); i++) {
				MarkdownLineRow row = lines.get(i);
				boolean match = row.getNode().equals(node);
				row.setHighlighted(match);
				if (match) idx = i;
			}
			if (idx >= 0) {
				listView.scrollTo(idx);
			}
		}
	}

	static class MarkdownLineRow {
		private final Node node;
		private final Association association;
		private final String text;
		private final RenderContext context;
		private final String style;
		private final BooleanProperty highlighted = new SimpleBooleanProperty(false);
		private final ObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>();
		private final ObjectProperty<Background> background = new SimpleObjectProperty<>();

		MarkdownLineRow(Node node, RenderContext context, Color initialColor) {
			this.node = node;
			this.association = containingAssociation(node);
			this.text = (node.getArtifact() != null && node.getArtifact().getData() instanceof LineArtifactData lineArtifactData)
					? lineArtifactData.getLine() : String.valueOf(node.getArtifact());
			this.context = context;
			this.style = buildStyle(context);

			highlighted.addListener((o, oldVal, newVal) -> updateBackground());
			backgroundColor.addListener((o, oldVal, newVal) -> updateBackground());
			backgroundColor.set(initialColor);
		}

		private static String buildStyle(RenderContext context) {
			StringBuilder style = new StringBuilder();
			switch (context.kind) {
				// heading level intentionally doesn't scale font size - cell height is fixed (see
				// createListView), so headings are distinguished by weight/color only, not size.
				case HEADING -> style.append("-fx-font-weight: bold; -fx-text-fill: #1a4d8f;");
				case CODE -> style.append("-fx-font-family: monospace;");
				case TABLE_HEADER_ROW -> style.append("-fx-font-family: monospace; -fx-font-weight: bold;");
				case TABLE_ROW -> style.append("-fx-font-family: monospace;");
				case THEMATIC_BREAK -> style.append("-fx-text-fill: #999999;");
				case PLAIN -> { /* no base style */ }
			}
			if (context.inBlockquote) {
				style.append(" -fx-font-style: italic;");
			}
			double indent = context.listDepth * 16.0 + (context.inBlockquote ? 8.0 : 0.0);
			if (indent > 0) {
				style.append(" -fx-padding: 0 0 0 ").append(indent).append(";");
			}
			return style.toString();
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

		String getStyle() {
			return style;
		}

		/** The classification this row's style was built from - exposed (package-visible) purely so
		 * {@code collectLines}'s dispatch can be unit-tested against the actual kind/list-depth/
		 * blockquote state, not just the resulting CSS string. */
		RenderContext getContext() {
			return context;
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
