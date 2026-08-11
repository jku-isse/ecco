package at.jku.cdl.ecco.adapter.java.view;

import at.jku.cdl.ecco.adapter.java.JavaASTPlugin;
import at.jku.cdl.ecco.adapter.java.artifactData.ASTNodeType;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTConstructorData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTTryData;
import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
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
import java.util.stream.Collectors;

/**
 * Shows a reconstructed, indented view of the AST-granularity Java adapter's (adapter/java-ast)
 * artifact tree - the {@code adapter/java-ast} counterpart of adapter/java's {@code JavaCodeViewer},
 * whose overall shape (indented pretty-print, {@link JavaSyntaxHighlighter} tokenizing, per-line
 * association-color highlighting, multi-file tabs) this reuses verbatim; only {@link #renderNode}
 * and its helpers differ, since this adapter's tree is built from entirely different artifact data
 * classes (package/type/field/method/statement nodes tagged with {@link ASTNodeType}, not
 * adapter/java's ClassArtifactData/MethodArtifactData/BlockArtifactData/...).
 * <p>
 * Like adapter/java's viewer, this does not reproduce the original source byte-for-byte - each
 * artifact already stores a JavaParser-pretty-printed text fragment (or, for if/try, a handful of
 * structured fields with no original text at all), so branches like if/else-if/try/catch are
 * reconstructed with synthetic bracing rather than replayed from the original formatting.
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
		return JavaASTPlugin.class.getName();
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
							// is too wide for the available space, which then gets clipped by this
							// ListView's fixed cell size below - HBox never wraps, so a long line just
							// extends past the visible width instead.
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

	/**
	 * Dispatches on {@link ASTNodeType} rather than concrete artifact-data class (unlike
	 * adapter/java's {@code instanceof}-per-class dispatch), since almost every node here is a
	 * {@link at.jku.cdl.ecco.adapter.java.artifactData.JavaASTSimpleStringData} distinguished only
	 * by its tagged type; {@link JavaASTConstructorData} and {@link JavaASTTryData} are the only
	 * two with their own dedicated fields (no ready-made text) and so get their own renderers.
	 */
	private void renderNode(Node n, int indent, List<JavaCodeLine> lines, Map<Node, Integer> indexByNode) {
		ArtifactData raw = n.getArtifact().getData();
		Association association = associationOf(n);

		if (!(raw instanceof JavaASTData data)) {
			addLine(n, association, String.valueOf(raw), indent, lines, indexByNode);
			for (Node child : n.getChildren()) {
				renderNode(child, indent + 1, lines, indexByNode);
			}
			return;
		}

		switch (data.getType()) {
			case PACKAGEDECLARATION -> {
				String packageName = textOf(data);
				if (!packageName.isEmpty()) {
					addLine(n, association, "package " + packageName + ";", indent, lines, indexByNode);
				}
			}
			case IMPORT_DECLARATION -> addLine(n, association, textOf(data), indent, lines, indexByNode);
			case MODULE_DECLARATION -> renderBlock(n, association, textOf(data), indent, lines, indexByNode);
			case MODULE_DIRECTIVE -> addLine(n, association, textOf(data), indent, lines, indexByNode);
			case TYPE_DECLARATION, ENUM_DECLARATION ->
					renderBlock(n, association, stripTrailingEmptyBody(textOf(data)), indent, lines, indexByNode);
			case FIELD_GROUP -> {
				for (Node child : n.getChildren()) {
					renderNode(child, indent, lines, indexByNode);
				}
			}
			case FIELD_DECLARATION, EXPRESSION, BODY_DECLARATION ->
					addLine(n, association, textOf(data), indent, lines, indexByNode);
			case INITIALIZER_DECLARATION -> addLine(n, association, textOf(data), indent, lines, indexByNode);
			case METHOD_DECLARATION -> renderMethod(n, association, textOf(data), indent, lines, indexByNode);
			case CONSTRUCTOR_DECLARATION ->
					renderConstructor(n, (JavaASTConstructorData) data, association, indent, lines, indexByNode);
			case STATEMENT -> renderStatement(n, association, textOf(data), indent, lines, indexByNode);
			case IF_STATEMENT -> renderIfStatement(n, indent, lines, indexByNode);
			case SWITCH_STATEMENT ->
					renderBlock(n, association, "switch (" + textOf(data) + ")", indent, lines, indexByNode);
			case SWITCH_ENTRIES -> renderSwitchEntry(n, association, textOf(data), indent, lines, indexByNode);
			case TRYBLOCK -> renderTry(n, (JavaASTTryData) data, association, indent, lines, indexByNode);
			case ENUM_CONSTANTS -> renderEnumConstant(n, association, textOf(data), indent, lines, indexByNode);
			default -> {
				// CATCHCLAUSE/FINALLY/IF_CONDITION/ELSE_BRANCH are only ever consumed directly by
				// their parent's renderer above (TRYBLOCK/IF_STATEMENT) - reachable here only if
				// this node type is ever nested somewhere unexpected, so fall back generically.
				addLine(n, association, textOf(data), indent, lines, indexByNode);
				for (Node child : n.getChildren()) {
					renderNode(child, indent + 1, lines, indexByNode);
				}
			}
		}
	}

	/** header + " {" / children / "}" - the common shape shared by type, switch, and if/try bodies. */
	private void renderBlock(Node n, Association association, String header, int indent, List<JavaCodeLine> lines, Map<Node, Integer> indexByNode) {
		addLine(n, association, header + " {", indent, lines, indexByNode);
		for (Node child : n.getChildren()) {
			renderNode(child, indent + 1, lines, indexByNode);
		}
		addLine(null, null, "}", indent, lines, indexByNode);
	}

	private void renderMethod(Node n, Association association, String signature, int indent, List<JavaCodeLine> lines, Map<Node, Integer> indexByNode) {
		if (n.getChildren().isEmpty()) {
			// abstract/interface method - signature already ends with ";" (see JavaASTReader:
			// MethodDeclaration.toString() with body set to null prints a semicolon, not a body).
			addLine(n, association, signature, indent, lines, indexByNode);
			return;
		}
		renderBlock(n, association, dropTrailingSemicolon(signature), indent, lines, indexByNode);
	}

	private void renderConstructor(Node n, JavaASTConstructorData data, Association association, int indent, List<JavaCodeLine> lines, Map<Node, Integer> indexByNode) {
		StringBuilder header = new StringBuilder();
		for (String annotation : data.getAnnotations()) {
			header.append(annotation).append(" ");
		}
		if (!data.getModifiers().isEmpty()) {
			header.append(String.join(" ", data.getModifiers())).append(" ");
		}
		if (!data.getTypeParameters().isEmpty()) {
			header.append("<").append(String.join(", ", data.getTypeParameters())).append("> ");
		}
		header.append(data.getName()).append("(").append(String.join(", ", data.getParameters())).append(")");
		if (!data.getThrowExceptions().isEmpty()) {
			header.append(" throws ").append(String.join(", ", data.getThrowExceptions()));
		}
		renderBlock(n, association, header.toString(), indent, lines, indexByNode);
	}

	private void renderStatement(Node n, Association association, String text, int indent, List<JavaCodeLine> lines, Map<Node, Integer> indexByNode) {
		if (n.getChildren().isEmpty()) {
			addLine(n, association, text, indent, lines, indexByNode);
			return;
		}
		// a STATEMENT with children is a loop/labeled/synchronized header (see JavaASTReader's
		// NodeWithBody case) - its captured text already carries a trailing ";" from the EmptyStmt
		// the reader substituted for the real body, which reads oddly right before an opening brace.
		renderBlock(n, association, dropTrailingSemicolon(text), indent, lines, indexByNode);
	}

	/**
	 * IF_STATEMENT's children are a flat list of IF_CONDITION nodes (one per if/else-if branch, per
	 * JavaASTReader.addIfCondition's cascading calls - not nested), the last of which may itself
	 * have a trailing ELSE_BRANCH child for a final "else". Reconstructed here as a single
	 * if/else-if/else chain rather than as nested blocks, to read naturally.
	 */
	private void renderIfStatement(Node n, int indent, List<JavaCodeLine> lines, Map<Node, Integer> indexByNode) {
		List<? extends Node> conditions = n.getChildren();
		for (int i = 0; i < conditions.size(); i++) {
			Node condNode = conditions.get(i);
			String condText = textOfNode(condNode);
			String prefix = i == 0 ? "if (" : "} else if (";
			addLine(condNode, associationOf(condNode), prefix + condText + ") {", indent, lines, indexByNode);

			Node elseBranch = null;
			for (Node child : condNode.getChildren()) {
				if (isType(child, ASTNodeType.ELSE_BRANCH)) {
					elseBranch = child;
				} else {
					renderNode(child, indent + 1, lines, indexByNode);
				}
			}
			if (elseBranch != null) {
				addLine(null, null, "} else {", indent, lines, indexByNode);
				for (Node child : elseBranch.getChildren()) {
					renderNode(child, indent + 1, lines, indexByNode);
				}
			}
		}
		addLine(null, null, "}", indent, lines, indexByNode);
	}

	private void renderSwitchEntry(Node n, Association association, String label, int indent, List<JavaCodeLine> lines, Map<Node, Integer> indexByNode) {
		String line = "DEFAULT".equals(label) ? "default:" : "case " + label + ":";
		addLine(n, association, line, indent, lines, indexByNode);
		for (Node child : n.getChildren()) {
			renderNode(child, indent + 1, lines, indexByNode);
		}
	}

	/**
	 * TRYBLOCK's children are a flat mix (per JavaASTReader.addTryStatement: catch clauses, then an
	 * optional finally, then the try-body statements last, all as direct siblings) rather than the
	 * try-body being under its own sub-node, so they're partitioned by type here before rendering.
	 */
	private void renderTry(Node n, JavaASTTryData data, Association association, int indent, List<JavaCodeLine> lines, Map<Node, Integer> indexByNode) {
		String header = data.getExpressions().isEmpty()
				? "try"
				: "try (" + String.join("; ", data.getExpressions()) + ")";
		addLine(n, association, header + " {", indent, lines, indexByNode);

		List<Node> catchClauses = new ArrayList<>();
		Node finallyNode = null;
		List<Node> bodyStatements = new ArrayList<>();
		for (Node child : n.getChildren()) {
			if (isType(child, ASTNodeType.CATCHCLAUSE)) {
				catchClauses.add(child);
			} else if (isType(child, ASTNodeType.FINALLY)) {
				finallyNode = child;
			} else {
				bodyStatements.add(child);
			}
		}

		for (Node statement : bodyStatements) {
			renderNode(statement, indent + 1, lines, indexByNode);
		}

		for (Node catchClause : catchClauses) {
			addLine(catchClause, associationOf(catchClause), "} catch (" + textOfNode(catchClause) + ") {", indent, lines, indexByNode);
			for (Node child : catchClause.getChildren()) {
				renderNode(child, indent + 1, lines, indexByNode);
			}
		}

		if (finallyNode != null) {
			// FINALLY's text is the whole finally block re-printed by JavaParser, braces included
			// (see JavaASTReader.addTryStatement) - not decomposed into children, so shown as-is
			// rather than wrapped in another synthetic "{"/"}" pair.
			addLine(finallyNode, associationOf(finallyNode), "} finally " + textOfNode(finallyNode), indent, lines, indexByNode);
		} else {
			addLine(null, null, "}", indent, lines, indexByNode);
		}
	}

	private void renderEnumConstant(Node n, Association association, String name, int indent, List<JavaCodeLine> lines, Map<Node, Integer> indexByNode) {
		List<Node> argumentNodes = new ArrayList<>();
		List<Node> otherChildren = new ArrayList<>();
		for (Node child : n.getChildren()) {
			if (isType(child, ASTNodeType.EXPRESSION)) {
				argumentNodes.add(child);
			} else {
				otherChildren.add(child);
			}
		}
		String arguments = argumentNodes.stream().map(this::textOfNode).collect(Collectors.joining(", "));
		String line = name + (argumentNodes.isEmpty() ? "" : "(" + arguments + ")") + (otherChildren.isEmpty() ? "," : " {");
		addLine(n, association, line, indent, lines, indexByNode);
		if (!otherChildren.isEmpty()) {
			for (Node child : otherChildren) {
				renderNode(child, indent + 1, lines, indexByNode);
			}
			addLine(null, null, "},", indent, lines, indexByNode);
		}
	}

	private Association associationOf(Node n) {
		return n.getArtifact() != null && n.getArtifact().getContainingNode() != null
				? n.getArtifact().getContainingNode().getContainingAssociation()
				: null;
	}

	private static boolean isType(Node n, ASTNodeType type) {
		ArtifactData d = n.getArtifact().getData();
		return d instanceof JavaASTData data && data.getType() == type;
	}

	private String textOfNode(Node n) {
		ArtifactData d = n.getArtifact().getData();
		return d instanceof JavaASTData data ? textOf(data) : String.valueOf(d);
	}

	private static String textOf(JavaASTData data) {
		return data.toString().strip();
	}

	private static String stripTrailingEmptyBody(String header) {
		return header.replaceAll("\\{\\s*}$", "").strip();
	}

	private static String dropTrailingSemicolon(String s) {
		return s.strip().replaceAll(";\\s*$", "");
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

		// A single artifact's stored text can itself carry a raw newline (e.g. a whole initializer
		// block or finally block, kept as one opaque pretty-printed fragment) - split into one row
		// per physical line rather than cramming several onto one, since a Text node renders an
		// embedded newline as an actual line break regardless of the row's fixed height, clipping
		// whatever doesn't fit. Every resulting row shares the same node/association/indent (they're
		// all still this one artifact). Only a genuinely trailing empty element - the split()
		// artifact of the text ending in a line terminator, not a real blank line - is dropped;
		// blank lines elsewhere are kept. indexByNode is registered on the first non-blank row so
		// "scroll to this node" lands on visible content rather than a leading blank line, falling
		// back to the first row if the whole artifact is blank.
		String[] physicalLines = LINE_BREAK.split(text, -1);
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
