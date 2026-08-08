package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.ArtifactDiagnostics;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lets the user reorder an ORDER-ambiguous node's children before committing -- the same Move
 * Up/Move Down + {@code ObservableList}-bound {@code TableView} pattern {@code CommitView}'s
 * folders table uses, reusing {@link ArtifactDiagnostics}'s per-child rendering (source line
 * numbers, etc.) for row labels so the list reads the same way the warnings table's diagnostic
 * already does.
 *
 * <p>When the ambiguous node's plugin (Java/C/Lilypond as of writing) has a registered {@code
 * AssociationInfoArtifactViewer} -- the same per-adapter syntax-highlighting code viewer {@code
 * ArtifactDetailView} embeds in the Artifacts tab -- it's reused here too, so the user sees the
 * actual highlighted source (not just a plain-text child label) and which child is selected, kept
 * in sync as they move rows around. Adapters without a registered viewer (e.g. plain text) simply
 * don't get this panel; the table-only layout is unchanged for them.
 *
 * <p>Purely an in-memory reorder -- does not mutate {@code node}. The caller applies the chosen
 * permutation via {@code Node.Op#setChildren} and flushes it to disk (see
 * {@code EccoService#writeCheckoutFile}) before committing.
 *
 * <p>Move Up/Move Down are also guided by the ambiguous artifact's own {@link PartialOrderGraph}:
 * a move that would swap two children whose relative order the graph has already fixed elsewhere
 * (directly or transitively - see {@link PartialOrderGraph.Op#canReach}) is disabled with an
 * explanatory tooltip, rather than silently letting the user recreate a conflict the graph already
 * resolved. Children the graph has no opinion on (including when there's no graph at all) stay as
 * freely reorderable as before.
 */
public class ReorderChildrenDialog extends Dialog<List<Node.Op>> {

	/** Default {@code FontIcon} size (8px) reads as barely-there for an icon-only button with no label text next to it. */
	private static final int ICON_SIZE = 16;

	@Inject
	private Set<ArtifactViewer> artifactViewers;
	@Inject
	private Set<AssociationInfoArtifactViewer> associationInfoArtifactViewers;

	public ReorderChildrenDialog(EccoService service, Node.Op node) {
		setTitle("Reorder Children");
		setHeaderText("Choose the order for the children of: " + node.getArtifact());
		setResizable(true);

		List<? extends Node.Op> children = node.getChildren();
		// setChildren() is also the exact call this dialog's own result gets applied through (see the
		// class javadoc) - reused here, temporarily, so the code preview can render what the file
		// would actually look like with the current proposed order, then immediately restored so
		// nothing about `node` is left mutated for as long as the dialog itself claims (only OK's
		// result should ever cause a lasting change).
		List<Node.Op> originalChildren = new ArrayList<>(children);
		List<String> labels = ArtifactDiagnostics.describeChildrenWithLines(node);
		// defensive: the 0-children fallback in ArtifactDiagnostics returns a 1-entry placeholder
		// list, which would otherwise desync from `children`'s real size (should be unreachable here
		// now that the real Node is threaded through, but this dialog should never index out of
		// bounds regardless).
		ObservableList<ChildEntry> rowData = FXCollections.observableArrayList();
		for (int i = 0; i < children.size(); i++) {
			String label = (labels.size() == children.size()) ? labels.get(i) : String.valueOf(children.get(i).getArtifact());
			rowData.add(new ChildEntry(children.get(i), label));
		}

		// used to disable a move that would cross a pair whose order the graph has already fixed -
		// see the class javadoc. Empty (never blocks anything) if there's no graph, matching this
		// dialog's pre-existing free-form behavior.
		PartialOrderGraph pog = node.getArtifact() != null ? node.getArtifact().getPartialOrderGraph() : null;
		Map<Node, PartialOrderGraph.Node> pogNodesByChild = pog != null ? pog.matchChildren(node) : Map.of();
		// shared by every "this position is fixed" affordance below (row dimming, the # column's
		// lock icon, and the Move buttons) so there's exactly one tooltip instance/message.
		Tooltip orderFixedTooltip = new Tooltip("Order fixed by an earlier commit");

		// The ORDER warning that opened this dialog is a property of the whole PartialOrderGraph (any
		// branch point anywhere in its accumulated history), not of the children actually present
		// here - it's possible for every one of them to already be fully determined (isRowFixed for
		// all) purely because the genuine ambiguity is between content from other, non-present variants.
		// When that happens there's no decision left to make, so this skips the OK/apply flow entirely
		// instead of presenting a table where every row and both buttons are disabled.
		boolean anyReorderable = false;
		for (int i = 0; i < rowData.size(); i++) {
			if (!isRowFixed(i, rowData, pogNodesByChild)) {
				anyReorderable = true;
				break;
			}
		}
		getDialogPane().getButtonTypes().addAll(anyReorderable
				? new ButtonType[]{ButtonType.OK, ButtonType.CANCEL}
				: new ButtonType[]{ButtonType.CLOSE});

		TableView<ChildEntry> table = new TableView<>();
		table.setEditable(false);
		table.setItems(rowData);
		table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		table.setPrefWidth(420);
		table.setPrefHeight(320);
		// dims a row whose position the graph has already fixed relative to both neighbors (nothing
		// to decide there) so the rows that actually need a decision - the real ambiguity - visually
		// stand out instead of every child looking equally up-for-grabs.
		table.setRowFactory(tv -> new TableRow<>() {
			@Override
			protected void updateItem(ChildEntry item, boolean empty) {
				super.updateItem(item, empty);
				boolean fixed = !empty && item != null && isRowFixed(getIndex(), rowData, pogNodesByChild);
				setStyle(fixed ? "-fx-opacity: 0.55;" : "");
				setTooltip(fixed ? orderFixedTooltip : null);
			}
		});

		TableColumn<ChildEntry, Integer> orderCol = new TableColumn<>("#");
		orderCol.setSortable(false);
		orderCol.setReorderable(false);
		orderCol.setCellFactory(col -> new TableCell<>() {
			private final FontIcon lockIcon = new FontIcon(Feather.LOCK);
			{
				lockIcon.setIconSize(12);
			}

			@Override
			protected void updateItem(Integer item, boolean empty) {
				super.updateItem(item, empty);
				if (empty) {
					setText(null);
					setGraphic(null);
					return;
				}
				setText(String.valueOf(getIndex() + 1));
				setGraphic(isRowFixed(getIndex(), rowData, pogNodesByChild) ? lockIcon : null);
			}
		});

		TableColumn<ChildEntry, String> labelCol = new TableColumn<>("Child");
		labelCol.setCellValueFactory(param -> param.getValue().labelProperty());
		labelCol.setCellFactory(TableColumns.wrappingCellFactory());

		table.getColumns().setAll(orderCol, labelCol);
		TableColumns.defaultWidth(orderCol, 36);
		TableColumns.growToFill(table, labelCol);

		Button moveUpButton = new Button(null, new FontIcon(Feather.ARROW_UP));
		Button moveDownButton = new Button(null, new FontIcon(Feather.ARROW_DOWN));
		((FontIcon) moveUpButton.getGraphic()).setIconSize(ICON_SIZE);
		((FontIcon) moveDownButton.getGraphic()).setIconSize(ICON_SIZE);
		moveUpButton.setDisable(true);
		moveDownButton.setDisable(true);

		// the code preview (if any) tracks whichever row is currently selected, so the user can see
		// exactly what they're moving, not just its plain-text label.
		Pane codePreview = findCodePreview(service, node);

		Runnable updateCodePreview = () -> {
			ChildEntry selected = table.getSelectionModel().getSelectedItem();
			if (selected == null || !(codePreview instanceof ArtifactViewer)) return;
			node.setChildren(rowData.stream().map(ChildEntry::getNode).collect(Collectors.toList()));
			try {
				((ArtifactViewer) codePreview).showTree(selected.getNode());
			} finally {
				node.setChildren(originalChildren);
			}
		};

		// icon-only buttons need a tooltip to stay self-explanatory - the base "Move Up"/"Move Down"
		// text is swapped for an explanation specifically when a move is blocked by the graph (not
		// just at the top/bottom of the list, which is self-evident from position alone).
		Tooltip moveUpTooltip = new Tooltip("Move Up");
		Tooltip moveDownTooltip = new Tooltip("Move Down");
		moveUpButton.setTooltip(moveUpTooltip);
		moveDownButton.setTooltip(moveDownTooltip);

		Runnable updateButtonStates = () -> {
			int idx = table.getSelectionModel().getSelectedIndex();

			boolean canMoveUp = idx > 0 && !swapBlocked(rowData.get(idx - 1), rowData.get(idx), pogNodesByChild);
			moveUpButton.setDisable(!canMoveUp);
			moveUpButton.setTooltip(idx > 0 && !canMoveUp ? orderFixedTooltip : moveUpTooltip);

			boolean canMoveDown = idx >= 0 && idx < rowData.size() - 1 && !swapBlocked(rowData.get(idx), rowData.get(idx + 1), pogNodesByChild);
			moveDownButton.setDisable(!canMoveDown);
			moveDownButton.setTooltip(idx >= 0 && idx < rowData.size() - 1 && !canMoveDown ? orderFixedTooltip : moveDownTooltip);
		};

		table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
			updateButtonStates.run();
			updateCodePreview.run();
		});

		moveUpButton.setOnAction(e -> {
			int idx = table.getSelectionModel().getSelectedIndex();
			if (idx > 0 && !swapBlocked(rowData.get(idx - 1), rowData.get(idx), pogNodesByChild)) {
				Collections.swap(rowData, idx, idx - 1);
				table.getSelectionModel().select(idx - 1);
				updateButtonStates.run();
				// a swap can change whether a row further out is now fixed/movable (e.g. the row that
				// used to be adjacent to whichever child just moved away) - not just the two swapped
				// rows themselves, so re-render every row rather than relying on the ObservableList
				// change notification alone to catch every affected one.
				table.refresh();
				// re-selecting the very entry that just moved (same object, new index) may not fire
				// the listener above at all - not relying on that to keep the preview current either.
				updateCodePreview.run();
			}
		});
		moveDownButton.setOnAction(e -> {
			int idx = table.getSelectionModel().getSelectedIndex();
			if (idx >= 0 && idx < rowData.size() - 1 && !swapBlocked(rowData.get(idx), rowData.get(idx + 1), pogNodesByChild)) {
				Collections.swap(rowData, idx, idx + 1);
				table.getSelectionModel().select(idx + 1);
				updateButtonStates.run();
				table.refresh();
				updateCodePreview.run();
			}
		});

		// the Move buttons are guaranteed permanently disabled when nothing's reorderable - pure
		// clutter next to the banner below explaining exactly that, so they're left out of the layout
		// entirely rather than shown disabled.
		HBox tableAndButtons;
		if (anyReorderable) {
			VBox buttons = new VBox(10, moveUpButton, moveDownButton);
			buttons.setAlignment(Pos.TOP_CENTER);
			tableAndButtons = new HBox(10, table, buttons);
		} else {
			tableAndButtons = new HBox(10, table);
		}
		HBox.setHgrow(table, Priority.ALWAYS);

		Region mainContent;
		if (codePreview != null) {
			SplitPane splitPane = new SplitPane(tableAndButtons, codePreview);
			splitPane.setOrientation(Orientation.HORIZONTAL);
			splitPane.setDividerPositions(0.4);
			splitPane.setPrefWidth(900);
			splitPane.setPrefHeight(480);
			mainContent = splitPane;
		} else {
			mainContent = tableAndButtons;
		}

		if (anyReorderable) {
			// unwrapped: the padding below (when nothing's reorderable) is supplied by wrapper instead,
			// so this stays the only place either container gets padded.
			if (codePreview == null) {
				tableAndButtons.setPadding(new Insets(10));
			}
			getDialogPane().setContent(mainContent);
		} else {
			Label noAmbiguityBanner = new Label("Nothing to reorder: these children's relative order is "
					+ "already fully determined by prior commits. The warning that opened this dialog "
					+ "comes from other content elsewhere in the version history that isn't part of this "
					+ "composition.");
			noAmbiguityBanner.setWrapText(true);
			noAmbiguityBanner.setMaxWidth(Double.MAX_VALUE);
			noAmbiguityBanner.setStyle("-fx-font-style: italic;");
			VBox wrapper = new VBox(8, noAmbiguityBanner, mainContent);
			wrapper.setPadding(new Insets(10));
			getDialogPane().setContent(wrapper);
		}

		if (!rowData.isEmpty()) {
			table.getSelectionModel().selectFirst();
		}

		setResultConverter(buttonType -> buttonType != ButtonType.OK ? null :
				rowData.stream().map(ChildEntry::getNode).collect(Collectors.toList()));
	}

	/**
	 * Whether swapping {@code earlier} and {@code later} (currently adjacent, in that relative order)
	 * would contradict a precedence relation the graph has already established, directly or
	 * transitively - {@code true} means don't allow it. Missing map entries (a child the graph has no
	 * matching node for at all) are treated as "no known constraint", same permissive fallback as
	 * having no graph at all.
	 */
	private static boolean swapBlocked(ChildEntry earlier, ChildEntry later, Map<Node, PartialOrderGraph.Node> pogNodesByChild) {
		PartialOrderGraph.Node earlierPogNode = pogNodesByChild.get(earlier.getNode());
		PartialOrderGraph.Node laterPogNode = pogNodesByChild.get(later.getNode());
		return earlierPogNode != null && laterPogNode != null && PartialOrderGraph.Op.canReach(earlierPogNode, laterPogNode);
	}

	/**
	 * Whether the child currently at {@code idx} is entirely settled - can't move up (blocked or
	 * already first) and can't move down (blocked or already last) - i.e. no genuine ambiguity
	 * involves it. Used to visually de-emphasize such rows, so what's left un-dimmed is exactly the
	 * ambiguity the user actually needs to resolve.
	 */
	private static boolean isRowFixed(int idx, List<ChildEntry> rowData, Map<Node, PartialOrderGraph.Node> pogNodesByChild) {
		boolean canMoveUp = idx > 0 && !swapBlocked(rowData.get(idx - 1), rowData.get(idx), pogNodesByChild);
		boolean canMoveDown = idx >= 0 && idx < rowData.size() - 1 && !swapBlocked(rowData.get(idx), rowData.get(idx + 1), pogNodesByChild);
		return !canMoveUp && !canMoveDown;
	}

	/**
	 * Looks up a registered {@code AssociationInfoArtifactViewer} for {@code node}'s plugin (the same
	 * Guice multibinding + plugin-id lookup {@code ArtifactDetailView#getArtifactViewers} uses), and
	 * returns it ready to embed, or {@code null} if none is registered for this adapter (e.g. plain
	 * text) or the service isn't initialized. Association-highlighting info is irrelevant here (this
	 * dialog isn't the live Artifacts panel), so it's initialized with none.
	 */
	private Pane findCodePreview(EccoService service, Node node) {
		if (!service.isInitialized()) return null;
		service.getInjector().injectMembers(this);
		if (this.associationInfoArtifactViewers == null) return null;

		String pluginId = ArtifactDetailView.getPluginId(node);
		if (pluginId == null) return null;

		for (AssociationInfoArtifactViewer viewer : this.associationInfoArtifactViewers) {
			if (viewer instanceof Pane && pluginId.equals(viewer.getPluginId())) {
				viewer.setAssociationInfos(null);
				return (Pane) viewer;
			}
		}
		return null;
	}

	private static class ChildEntry {
		private final Node.Op node;
		private final SimpleStringProperty label;

		ChildEntry(Node.Op node, String label) {
			this.node = node;
			this.label = new SimpleStringProperty(label);
		}

		Node.Op getNode() {
			return this.node;
		}

		SimpleStringProperty labelProperty() {
			return this.label;
		}
	}
}
