package at.jku.isse.ecco.adapter.java.view;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.java.JavaPlugin;
import at.jku.isse.ecco.adapter.java.data.BlockArtifactData;
import at.jku.isse.ecco.adapter.java.data.ClassArtifactData;
import at.jku.isse.ecco.adapter.java.data.FieldArtifactData;
import at.jku.isse.ecco.adapter.java.data.ImportArtifactData;
import at.jku.isse.ecco.adapter.java.data.LineArtifactData;
import at.jku.isse.ecco.adapter.java.data.MethodArtifactData;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.util.Callback;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows a reconstructed, indented view of a Java artifact tree. The tree does not preserve the
 * original source byte-for-byte (each artifact already stores a summarized text fragment, e.g. a
 * whole field declaration or statement, rather than a token), so this pretty-prints the tree back
 * into Java-looking code rather than reproducing the original file exactly.
 * <p>
 * A commit (or any other composed view) can span multiple Java files, so {@link #showTree(Node)}
 * walks up to the true root of whatever tree was passed in, then finds every distinct Java file
 * reachable from there (each is its own {@link PluginArtifactData} node) and shows each as its own
 * tab, defaulting to the tab/line containing the node that was actually selected. If only one file
 * is present, the tab chrome is skipped and the code is shown directly, matching the single-file
 * Artifacts panel case.
 */
public class JavaCodeViewer extends BorderPane implements AssociationInfoArtifactViewer {

	private static final String INDENT_UNIT = "    ";

	private final HashMap<String, AssociationInfo> associationInfos = new HashMap<>();
	private final HashMap<String, PropertyChangeListener> associationListeners = new HashMap<>();
	private final Map<Node, ObservableList<JavaCodeLine>> linesByFile = new LinkedHashMap<>();
	private final Callback<ListView<JavaCodeLine>, ListCell<JavaCodeLine>> cellFactory = createCellFactory();
	private final SplitPane splitPane;
	private final TextArea taInfo;

	public JavaCodeViewer() {
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
		// since that only tracks nodes that produced a rendered line - the file node itself
		// never would, and would otherwise fail to select any tab at all
		Node selectedFileNode = findContainingFileNode(n);
		Integer selectedIndex = null;

		for (Node fileNode : fileNodes) {
			List<JavaCodeLine> built = new ArrayList<>();
			Map<Node, Integer> indexByNode = new HashMap<>();
			for (Node child : fileNode.getChildren()) {
				renderNode(child, 0, built, indexByNode);
			}

			ObservableList<JavaCodeLine> lines = FXCollections.observableArrayList(built);
			linesByFile.put(fileNode, lines);

			if (fileNode.equals(selectedFileNode)) {
				selectedIndex = indexByNode.get(n);
			}
		}

		rebuildView(selectedFileNode, selectedIndex);
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
		return JavaPlugin.class.getName();
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
			ObservableList<JavaCodeLine> lines = linesByFile.isEmpty()
					? FXCollections.observableArrayList()
					: linesByFile.values().iterator().next();
			ListView<JavaCodeLine> listView = createListView(lines);
			splitPane.getItems().set(0, listView);
			scrollToLine(listView, selectedIndex);
			return;
		}

		TabPane tabPane = new TabPane();
		Tab tabToSelect = null;
		for (Map.Entry<Node, ObservableList<JavaCodeLine>> entry : linesByFile.entrySet()) {
			Node fileNode = entry.getKey();
			ListView<JavaCodeLine> listView = createListView(entry.getValue());

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

	private void scrollToLine(ListView<JavaCodeLine> listView, Integer index) {
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

	private ListView<JavaCodeLine> createListView(ObservableList<JavaCodeLine> lines) {
		ListView<JavaCodeLine> listView = new ListView<>(lines);
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

	private Callback<ListView<JavaCodeLine>, ListCell<JavaCodeLine>> createCellFactory() {
		return new Callback<>() {
			@Override
			public ListCell<JavaCodeLine> call(ListView<JavaCodeLine> param) {
				return new ListCell<>() {
					@Override
					protected void updateItem(JavaCodeLine line, boolean empty) {
						super.updateItem(line, empty);
						if (empty || line == null) {
							setText(null);
							setGraphic(null);
							backgroundProperty().unbind();
						} else {
							// HBox, not TextFlow: TextFlow wraps onto multiple lines once its content
							// is too wide for the available space (comments are usually the longest
							// token on a line, so they're what most often triggers it), which then
							// gets clipped by this ListView's fixed cell size below - HBox never
							// wraps, so a long line just extends past the visible width instead
							// (matching how LilypondCodeViewer, which never had this bug, already
							// renders its rows).
							HBox flow = new HBox();
							flow.setAlignment(Pos.BASELINE_LEFT);
							flow.setOnMouseEntered(e -> showAssociationInfo(line.getAssociation()));

							Text indentText = new Text(INDENT_UNIT.repeat(line.getIndent()));
							flow.getChildren().add(indentText);

							for (JavaSyntaxHighlighter.Token token : line.getTokens()) {
								Text text = new Text(token.text());
								JavaSyntaxHighlighter.Style style = token.style();
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

	private void renderNode(Node n, int indent, List<JavaCodeLine> lines, Map<Node, Integer> indexByNode) {
		ArtifactData d = n.getArtifact().getData();
		Association association = n.getArtifact().getContainingNode() != null
				? n.getArtifact().getContainingNode().getContainingAssociation()
				: null;

		if (d instanceof ClassArtifactData classData) {
			addLine(n, association, "class " + simpleName(classData.getName()) + " {", indent, lines, indexByNode);
			for (Node child : n.getChildren()) {
				renderNode(child, indent + 1, lines, indexByNode);
			}
			addLine(null, null, "}", indent, lines, indexByNode);

		} else if (d instanceof MethodArtifactData methodData) {
			addLine(n, association, methodData.toString() + " {", indent, lines, indexByNode);
			for (Node child : n.getChildren()) {
				renderNode(child, indent + 1, lines, indexByNode);
			}
			addLine(null, null, "}", indent, lines, indexByNode);

		} else if (d instanceof BlockArtifactData blockData) {
			addLine(n, association, blockData.toString() + " {", indent, lines, indexByNode);
			for (Node child : n.getChildren()) {
				renderNode(child, indent + 1, lines, indexByNode);
			}
			addLine(null, null, "}", indent, lines, indexByNode);

		} else if (d instanceof ImportArtifactData importData) {
			addLine(n, association, importData.toString() + ";", indent, lines, indexByNode);

		} else if (d instanceof FieldArtifactData fieldData) {
			addLine(n, association, withTrailingSemicolon(fieldData.toString()), indent, lines, indexByNode);
			for (Node child : n.getChildren()) {
				renderNode(child, indent + 1, lines, indexByNode);
			}

		} else if (d instanceof LineArtifactData lineData) {
			addLine(n, association, withTrailingSemicolon(lineData.toString()), indent, lines, indexByNode);
			for (Node child : n.getChildren()) {
				renderNode(child, indent + 1, lines, indexByNode);
			}

		} else {
			addLine(n, association, String.valueOf(d), indent, lines, indexByNode);
			for (Node child : n.getChildren()) {
				renderNode(child, indent + 1, lines, indexByNode);
			}
		}
	}

	private void addLine(Node n, Association association, String text, int indent, List<JavaCodeLine> lines, Map<Node, Integer> indexByNode) {
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

		// A single artifact's stored text can itself carry a raw newline (most commonly a
		// multi-line block comment, or a trailing // comment followed by more of the original
		// source on the next physical line) - split into one row per physical line rather than
		// cramming several onto one, since a Text node renders an embedded newline as an actual
		// line break regardless of the row's fixed height, clipping whatever doesn't fit. Every
		// resulting row shares the same node/association/indent (they're all still this one
		// artifact). Only a genuinely trailing empty element - the split() artifact of the text
		// ending in a line terminator, not a real blank line - is dropped; blank lines elsewhere
		// (e.g. a blank paragraph inside a block comment) are kept. indexByNode is registered on
		// the first non-blank row so "scroll to this node" lands on visible content rather than a
		// leading blank line, falling back to the first row if the whole artifact is blank.
		String[] physicalLines = text.split("\r\n|\r|\n", -1);
		// Tokenizing the whole (possibly multi-line) text at once, rather than each physicalLine in
		// isolation, lets JavaSyntaxHighlighter recognize a comment/string that opens on one row and
		// closes on a later one; tokenRows splits at the exact same points as physicalLines above, so
		// row i's tokens always correspond to physicalLines[i].
		List<List<JavaSyntaxHighlighter.Token>> tokenRows = JavaSyntaxHighlighter.tokenizeLines(text);
		Integer firstIndex = null;
		Integer firstContentIndex = null;
		for (int i = 0; i < physicalLines.length; i++) {
			String physicalLine = physicalLines[i];
			if (physicalLine.isEmpty() && i > 0 && i == physicalLines.length - 1) {
				continue;
			}
			JavaCodeLine line = new JavaCodeLine(n, association, physicalLine, indent, tokenRows.get(i));
			line.backgroundColor().set(bgCol);
			if (firstIndex == null) {
				firstIndex = lines.size();
			}
			if (firstContentIndex == null && !physicalLine.isEmpty()) {
				firstContentIndex = lines.size();
			}
			lines.add(line);
		}
		if (n != null && firstIndex != null) {
			indexByNode.put(n, firstContentIndex != null ? firstContentIndex : firstIndex);
		}
	}

	private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\r|\n");

	// The actual statement lives on the first physical line; any further lines are leftover
	// original-source text (see addLine()'s comment), so the semicolon belongs after the first
	// line, not after whatever text happens to trail it.
	private static String withTrailingSemicolon(String text) {
		Matcher m = LINE_BREAK.matcher(text);
		if (!m.find()) {
			return appendSemicolonIfNeeded(text);
		}
		return appendSemicolonIfNeeded(text.substring(0, m.start())) + text.substring(m.start());
	}

	private static String appendSemicolonIfNeeded(String line) {
		String trimmed = line.stripTrailing();
		if (trimmed.isEmpty() || trimmed.endsWith(";") || trimmed.endsWith("{") || trimmed.endsWith("}") || trimmed.endsWith(":")) {
			return line;
		}
		return line + ";";
	}

	private static String simpleName(String qualifiedName) {
		int lastDot = qualifiedName.lastIndexOf('.');
		return lastDot < 0 ? qualifiedName : qualifiedName.substring(lastDot + 1);
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
				for (ObservableList<JavaCodeLine> lines : linesByFile.values()) {
					for (JavaCodeLine line : lines) {
						if (line.getAssociation() != null && aId.equals(line.getAssociation().getId())) {
							line.backgroundColor().set((Color) evt.getNewValue());
						}
					}
				}
			}
		};
	}
}
