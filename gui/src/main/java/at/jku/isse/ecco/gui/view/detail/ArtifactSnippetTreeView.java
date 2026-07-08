package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.gui.CategoricalColorPalette;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shows an artifact tree as plain snippets (the artifact's own text, e.g. a line of Java or
 * LilyPond source) with no metadata columns, for a compact read-only preview of an
 * association's content. Snippets are tinted by artifact kind (the concrete data class,
 * e.g. LineArtifactData, ClassArtifactData) so different kinds of content are visually
 * distinguishable; the legend doubles as a set of toggles to filter snippets by kind.
 */
public class ArtifactSnippetTreeView extends BorderPane {

	private static final Color SNIPPET_BORDER = Color.web("#b0b0b0");
	private static final Color SNIPPET_TEXT = Color.web("#202020");

	private final TreeView<Node> treeView = new TreeView<>();
	private final FlowPane legend = new FlowPane(12, 4);
	private final Map<String, Color> kindColors = new LinkedHashMap<>();
	private final Set<String> visibleKinds = new LinkedHashSet<>();

	private RootNode currentRootNode;

	public ArtifactSnippetTreeView() {
		legend.setPadding(new Insets(4, 8, 4, 8));
		this.setTop(legend);
		this.setCenter(treeView);

		treeView.setCellFactory(tv -> new TreeCell<>() {
			@Override
			protected void updateItem(Node item, boolean empty) {
				super.updateItem(item, empty);

				Artifact<?> artifact = (empty || item == null) ? null : item.getArtifact();
				String snippet = artifact == null ? null : item.toString();

				if (snippet == null || snippet.isEmpty()) {
					setText(null);
					setStyle(null);
				} else {
					setText(snippet);
					setFont(Font.font("Monospaced", getFont().getSize()));
					setStyle(snippetStyle(colorForKind(kindOf(artifact))));
				}
			}
		});

		this.setRootNode(null);
	}

	public final void setRootNode(RootNode rootNode) {
		this.currentRootNode = rootNode;
		kindColors.clear();
		visibleKinds.clear();
		legend.getChildren().clear();

		if (rootNode == null) {
			treeView.setRoot(null);
			return;
		}

		// precompute kind colors for the whole tree up front, so the legend is complete and
		// colors are stable regardless of which rows the (virtualized) tree actually renders
		rootNode.traverse(node -> {
			if (node.getArtifact() != null) {
				colorForKind(kindOf(node.getArtifact()));
			}
		});
		visibleKinds.addAll(kindColors.keySet());

		for (Map.Entry<String, Color> entry : kindColors.entrySet()) {
			legend.getChildren().add(legendToggle(entry.getKey(), entry.getValue()));
		}

		rebuildTree();
	}

	private void rebuildTree() {
		if (currentRootNode == null) {
			treeView.setRoot(null);
			return;
		}

		TreeItem<Node> root = new TreeItem<>(currentRootNode);
		buildVisibleChildren(currentRootNode, root);
		treeView.setRoot(root);
		treeView.setShowRoot(false);
		expandAll(root);
	}

	/**
	 * Builds item's children from node's children, keeping only the ones whose kind is
	 * currently toggled on or that have a visible descendant (so ancestors of a visible
	 * snippet stay in place for indentation even if their own kind is filtered out).
	 *
	 * @return true if node itself is visible or has a visible descendant.
	 */
	private boolean buildVisibleChildren(Node node, TreeItem<Node> item) {
		boolean anyVisible = isKindVisible(node);

		for (Node child : node.getChildren()) {
			TreeItem<Node> childItem = new TreeItem<>(child);
			if (buildVisibleChildren(child, childItem)) {
				item.getChildren().add(childItem);
				anyVisible = true;
			}
		}

		return anyVisible;
	}

	private boolean isKindVisible(Node node) {
		Artifact<?> artifact = node.getArtifact();
		return artifact == null || visibleKinds.contains(kindOf(artifact));
	}

	private static String kindOf(Artifact<?> artifact) {
		String className = artifact.getData().getClass().getSimpleName();
		if (className.endsWith("ArtifactData"))
			className = className.substring(0, className.length() - "ArtifactData".length());
		return className.isEmpty() ? "Artifact" : className;
	}

	private Color colorForKind(String kind) {
		return kindColors.computeIfAbsent(kind, k -> {
			int index = kindColors.size();
			return index < CategoricalColorPalette.size() ? CategoricalColorPalette.colorAt(index) : CategoricalColorPalette.OTHER;
		});
	}

	private static String snippetStyle(Color kindColor) {
		Color tint = CategoricalColorPalette.tintForBackground(kindColor);
		return "-fx-border-color: " + toRgbCss(SNIPPET_BORDER) + ";" +
				"-fx-border-radius: 4;" +
				"-fx-background-radius: 4;" +
				"-fx-background-color: " + toRgbCss(tint) + ";" +
				"-fx-text-fill: " + toRgbCss(SNIPPET_TEXT) + ";" +
				"-fx-padding: 2 8 2 8;";
	}

	private static String toRgbCss(Color color) {
		return String.format("rgb(%d,%d,%d)", Math.round(color.getRed() * 255), Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255));
	}

	private HBox legendToggle(String kind, Color color) {
		Region swatch = new Region();
		swatch.setMinSize(12, 12);
		swatch.setMaxSize(12, 12);
		swatch.setStyle("-fx-background-color: " + toRgbCss(color) + "; -fx-background-radius: 2;");

		CheckBox checkBox = new CheckBox(kind);
		checkBox.setSelected(true);
		checkBox.selectedProperty().addListener((observable, wasSelected, isSelected) -> {
			if (isSelected)
				visibleKinds.add(kind);
			else
				visibleKinds.remove(kind);
			rebuildTree();
		});

		HBox box = new HBox(4, swatch, checkBox);
		box.setAlignment(Pos.CENTER_LEFT);
		return box;
	}

	private void expandAll(TreeItem<Node> item) {
		item.setExpanded(true);
		for (TreeItem<Node> child : item.getChildren()) {
			expandAll(child);
		}
	}

}