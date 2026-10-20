package at.jku.isse.ecco.gui.view.detail;

import java.util.IdentityHashMap;
import javafx.scene.control.CheckBox;
import javafx.collections.transformation.FilteredList;
import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.adapter.dispatch.TextFileFormat;
import at.jku.isse.ecco.gui.RepositoryAccess;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.LlmPreferences;
import at.jku.isse.ecco.service.llm.LlmOrderSuggestionClient;
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
 * folders table uses, each row labelled with its child's text ({@link ArtifactDiagnostics#textOf})
 * and its place in the file in the # column. For a large file only the rows that may move are
 * shown, with three around each ("Undecided only"), and "Next undecided" goes to the next of them.
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

	/**
	 * Proposes an order for rows of a file (see {@link LlmOrderSuggestionClient}); the dialog asks it
	 * from "Suggest order". Pluggable so tests need no LLM.
	 */
	@FunctionalInterface
	public interface OrderSuggester {
		LlmOrderSuggestionClient.Suggestion suggest(String path, List<LlmOrderSuggestionClient.Row> rows);
	}

	/** The LLM endpoint and model of the preferences, or null if no model is chosen there. */
	static OrderSuggester suggesterFromPreferences() {
		String model = LlmPreferences.getModelName();
		if (model == null || model.isBlank())
			return null;
		LlmOrderSuggestionClient client = new LlmOrderSuggestionClient(LlmPreferences.getEndpointUrl(), model);
		return client::suggestOrder;
	}

	public ReorderChildrenDialog(EccoService service, Node.Op node) {
		this(service, node, suggesterFromPreferences());
	}

	/** @param suggester asked for an order by "Suggest order"; null disables it (no model chosen) */
	public ReorderChildrenDialog(EccoService service, Node.Op node, OrderSuggester suggester) {
		at.jku.isse.ecco.gui.Dialogs.ownByFocusedWindow(this);
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
		// the code preview (if any) shows the selected row's source (below); its viewer also knows the
		// source of artifacts that don't hold their text (Python, TypeScript) - shown and sent to the
		// LLM instead of their labels (a Python statement's label is its serialized bytes)
		Pane codePreview = findCodePreview(service, node);
		List<String> sourceTexts = codePreview instanceof ArtifactViewer viewer ? viewer.sourceTexts(children) : List.of();
		// defensive: the 0-children fallback in ArtifactDiagnostics returns a 1-entry placeholder
		// list, which would otherwise desync from `children`'s real size (should be unreachable here
		// now that the real Node is threaded through, but this dialog should never index out of
		// bounds regardless).
		ObservableList<ChildEntry> rowData = FXCollections.observableArrayList();
		// a text file's final newline and format are no lines to move: they stay last (TextFileFormat)
		List<Node.Op> markers = new ArrayList<>();
		for (int i = 0; i < children.size(); i++) {
			if (TextFileFormat.isMarker(children.get(i))) {
				markers.add(children.get(i));
				continue;
			}
			String source = i < sourceTexts.size() && sourceTexts.get(i) != null && !sourceTexts.get(i).isBlank() ? oneLine(sourceTexts.get(i)) : null;
			// the text alone: its place is in the # column; the line the adapter recorded is that of the
			// variant it was committed with - two rows to order said "line 31" both
			String label = source != null ? source : ArtifactDiagnostics.textOf(children.get(i));
			String text = source != null ? source : ArtifactDiagnostics.textOf(children.get(i));
			rowData.add(new ChildEntry(children.get(i), label, text));
		}

		// used to disable a move that would cross a pair whose order the graph has already fixed -
		// see the class javadoc. The graph is the repository's, which a commit or a recorded order
		// changes holding the service's lock: what this dialog asks of it is copied holding the lock,
		// in the background (it was asked on the FX thread, while they changed it), and the dialog is
		// built once it is - until then it says so, and can be cancelled
		getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
		Label reading = new Label(service.isInitialized() ? "Reading the order graph..." : "The repository is closed.");
		reading.setPadding(new Insets(10));
		getDialogPane().setContent(reading);
		setResultConverter(buttonType -> null);
		RepositoryAccess.read(service, "Order graph", svc -> Order.of(node),
				order -> this.build(node, suggester, originalChildren, codePreview, rowData, markers, order),
				failure -> reading.setText("The order graph could not be read: " + failure));
	}

	/** Whether the dialog is built, with what the order graph says - for tests. */
	boolean isBuilt() {
		return this.built;
	}

	private boolean built;

	/** Builds the dialog's content, [order] copied from the order graph. */
	private void build(Node.Op node, OrderSuggester suggester, List<Node.Op> originalChildren, Pane codePreview,
					   ObservableList<ChildEntry> rowData, List<Node.Op> markers, Order order) {
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
			if (!isRowFixed(i, rowData, order)) {
				anyReorderable = true;
				break;
			}
		}
		getDialogPane().getButtonTypes().setAll(anyReorderable
				? new ButtonType[]{ButtonType.OK, ButtonType.CANCEL}
				: new ButtonType[]{ButtonType.CLOSE});

		// which rows are fixed and where each is, worked out once per change - it was for every cell
		// drawn, two graph searches each; and the rows shown: with "Undecided only" those that may
		// move and three around each - for a file of thousands of lines the few to decide were to be
		// found among them all
		Map<ChildEntry, Boolean> fixedRows = new IdentityHashMap<>();
		Map<ChildEntry, Integer> placeOf = new IdentityHashMap<>();
		Set<ChildEntry> nearUndecided = Collections.newSetFromMap(new IdentityHashMap<>());
		CheckBox undecidedOnly = new CheckBox("Undecided only (and " + SUGGESTION_CONTEXT_ROWS + " rows around)");
		undecidedOnly.setSelected(rowData.size() > LARGE_FILE_ROWS);
		FilteredList<ChildEntry> shownRows = new FilteredList<>(rowData);
		// rows [from, to] checked against the graph again - two searches each
		java.util.function.BiConsumer<Integer, Integer> checkRows = (from, to) -> {
			for (int i = Math.max(0, from); i <= Math.min(rowData.size() - 1, to); i++) {
				fixedRows.put(rowData.get(i), isRowFixed(i, rowData, order));
				placeOf.put(rowData.get(i), i);
			}
		};
		// the rows shown, from what is known of each - no search
		Runnable showRows = () -> {
			nearUndecided.clear();
			for (int i = 0; i < rowData.size(); i++)
				if (!fixedRows.getOrDefault(rowData.get(i), false))
					for (int j = Math.max(0, i - SUGGESTION_CONTEXT_ROWS); j <= Math.min(rowData.size() - 1, i + SUGGESTION_CONTEXT_ROWS); j++)
						nearUndecided.add(rowData.get(j));
			shownRows.setPredicate(undecidedOnly.isSelected() ? row -> nearUndecided.contains(row) : null);
		};
		Runnable refreshRows = () -> {
			fixedRows.clear();
			placeOf.clear();
			checkRows.accept(0, rowData.size() - 1);
			showRows.run();
		};
		refreshRows.run();
		// a move swaps rows i and i+1: only rows i-1 to i+2 can change - all rows were checked again,
		// twice (a swap is two changes of the list)
		int[] swapAt = {-1};
		rowData.addListener((javafx.collections.ListChangeListener<ChildEntry>) change -> {
			if (swapAt[0] >= 0) {
				checkRows.accept(swapAt[0] - 1, swapAt[0] + 2);
				showRows.run();
			} else {
				refreshRows.run();
			}
		});
		undecidedOnly.selectedProperty().addListener((o, was, only) -> refreshRows.run());

		TableView<ChildEntry> table = new TableView<>();
		table.setEditable(false);
		table.setItems(shownRows);
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
				boolean fixed = !empty && item != null && fixedRows.getOrDefault(item, false);
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
				// its place in the file, also while only some rows are shown
				ChildEntry row = getTableRow() != null ? getTableRow().getItem() : null;
				if (row == null && getIndex() >= 0 && getIndex() < getTableView().getItems().size())
					row = getTableView().getItems().get(getIndex());
				setText(row == null ? null : String.valueOf(placeOf.getOrDefault(row, getIndex()) + 1));
				setGraphic(row != null && fixedRows.getOrDefault(row, false) ? lockIcon : null);
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

		Runnable updateCodePreview = () -> {
			ChildEntry selected = table.getSelectionModel().getSelectedItem();
			if (selected == null || !(codePreview instanceof ArtifactViewer)) return;
			node.setChildren(withMarkers(rowData, markers));
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
			// its place among all rows: only some may be shown
			int idx = rowData.indexOf(table.getSelectionModel().getSelectedItem());

			boolean canMoveUp = idx > 0 && !swapBlocked(rowData.get(idx - 1), rowData.get(idx), order);
			moveUpButton.setDisable(!canMoveUp);
			moveUpButton.setTooltip(idx > 0 && !canMoveUp ? orderFixedTooltip : moveUpTooltip);

			boolean canMoveDown = idx >= 0 && idx < rowData.size() - 1 && !swapBlocked(rowData.get(idx), rowData.get(idx + 1), order);
			moveDownButton.setDisable(!canMoveDown);
			moveDownButton.setTooltip(idx >= 0 && idx < rowData.size() - 1 && !canMoveDown ? orderFixedTooltip : moveDownTooltip);
		};

		table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
			updateButtonStates.run();
			updateCodePreview.run();
		});

		moveUpButton.setOnAction(e -> {
			ChildEntry moved = table.getSelectionModel().getSelectedItem();
			int idx = rowData.indexOf(moved);
			if (idx > 0 && !swapBlocked(rowData.get(idx - 1), rowData.get(idx), order)) {
				swapAt[0] = idx - 1;
				try {
					Collections.swap(rowData, idx, idx - 1);
				} finally {
					swapAt[0] = -1;
				}
				table.getSelectionModel().select(moved);
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
			ChildEntry moved = table.getSelectionModel().getSelectedItem();
			int idx = rowData.indexOf(moved);
			if (idx >= 0 && idx < rowData.size() - 1 && !swapBlocked(rowData.get(idx), rowData.get(idx + 1), order)) {
				swapAt[0] = idx;
				try {
					Collections.swap(rowData, idx, idx + 1);
				} finally {
					swapAt[0] = -1;
				}
				table.getSelectionModel().select(moved);
				updateButtonStates.run();
				table.refresh();
				updateCodePreview.run();
			}
		});

		// "Suggest order": an LLM proposes an order of the rows that may move (see suggestOrder);
		// shown in the table to review and edit, recorded only with OK
		Button suggestButton = new Button("Suggest order", new FontIcon(Feather.ZAP));
		suggestButton.setMinWidth(Region.USE_PREF_SIZE); // the text next to it wraps, the button keeps its label
		Label suggestStatus = new Label();
		suggestStatus.setWrapText(true);
		suggestStatus.setMaxWidth(Double.MAX_VALUE);
		if (suggester == null) {
			suggestButton.setDisable(true);
			// a disabled control shows no tooltip: the reason goes next to it
			suggestStatus.setText("To have an LLM suggest an order, choose a model in Preferences (LLM).");
		}
		suggestButton.setOnAction(e -> this.suggestOrder(suggester, node, rowData, order, table, suggestButton, suggestStatus,
				() -> {
					updateButtonStates.run();
					updateCodePreview.run();
				}));

		// the next row that may move, after the one selected (and round)
		Button nextUndecided = new Button("Next undecided", new FontIcon(Feather.CHEVRONS_DOWN));
		nextUndecided.setTooltip(new Tooltip("Select the next row whose place is not fixed"));
		nextUndecided.setOnAction(e -> {
			int from = rowData.indexOf(table.getSelectionModel().getSelectedItem());
			for (int step = 1; step <= rowData.size(); step++) {
				ChildEntry row = rowData.get(Math.floorMod(from + step, rowData.size()));
				if (!fixedRows.getOrDefault(row, false)) {
					table.getSelectionModel().select(row);
					table.scrollTo(row);
					break;
				}
			}
		});
		HBox filterBar = new HBox(10, undecidedOnly, nextUndecided);
		filterBar.setAlignment(Pos.CENTER_LEFT);

		// the Move buttons are guaranteed permanently disabled when nothing's reorderable - pure
		// clutter next to the banner below explaining exactly that, so they're left out of the layout
		// entirely rather than shown disabled.
		HBox tableAndButtons;
		if (anyReorderable) {
			VBox buttons = new VBox(10, moveUpButton, moveDownButton);
			buttons.setAlignment(Pos.TOP_CENTER);
			VBox tableAndSuggestion = new VBox(8, filterBar, table, suggestButton, suggestStatus);
			VBox.setVgrow(table, Priority.ALWAYS);
			tableAndButtons = new HBox(10, tableAndSuggestion, buttons);
			HBox.setHgrow(tableAndSuggestion, Priority.ALWAYS);
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

		setResultConverter(buttonType -> buttonType != ButtonType.OK ? null : withMarkers(rowData, markers));
		this.built = true;
		// shown already, while it was read: as large as its content
		if (getDialogPane().getScene() != null && getDialogPane().getScene().getWindow() != null)
			getDialogPane().getScene().getWindow().sizeToScene();
	}

	/** Rows sent around the ones that may move, as context; shown around them with "Undecided only". */
	private static final int SUGGESTION_CONTEXT_ROWS = 3;
	/** Rows of a file above which "Undecided only" is on from the start. */
	static final int LARGE_FILE_ROWS = 40;

	/**
	 * Asks [suggester] for an order of the window of rows around those that may move (with a few
	 * rows of context before and after), in the background, and puts it into the table - for the
	 * user to review, edit, and record with OK. The order graph's fixed precedences always win
	 * (LlmOrderSuggestionClient#respecting): a suggestion never breaks an order a commit fixed.
	 */
	private void suggestOrder(OrderSuggester suggester, Node.Op node, ObservableList<ChildEntry> rowData, Order order,
							  TableView<ChildEntry> table, Button suggestButton, Label status, Runnable afterReorder) {
		int first = -1, last = -1;
		for (int i = 0; i < rowData.size(); i++) {
			if (!isRowFixed(i, rowData, order)) {
				if (first < 0)
					first = i;
				last = i;
			}
		}
		if (first < 0)
			return;
		int from = Math.max(0, first - SUGGESTION_CONTEXT_ROWS), to = Math.min(rowData.size() - 1, last + SUGGESTION_CONTEXT_ROWS);
		List<ChildEntry> window = new ArrayList<>(rowData.subList(from, to + 1));
		if (window.size() > LlmOrderSuggestionClient.MAX_ROWS) {
			status.setText(window.size() + " lines are open to reordering - too many for a suggestion (at most " + LlmOrderSuggestionClient.MAX_ROWS + ").");
			return;
		}
		List<LlmOrderSuggestionClient.Row> rows = new ArrayList<>();
		for (int i = 0; i < window.size(); i++)
			rows.add(new LlmOrderSuggestionClient.Row(window.get(i).getText(), !isRowFixed(from + i, rowData, order)));
		boolean[][] mustPrecede = mustPrecede(window, order);
		String path = ArtifactDiagnostics.describePath(node);

		int windowFrom = from;
		javafx.concurrent.Task<LlmOrderSuggestionClient.Suggestion> task = new javafx.concurrent.Task<>() {
			@Override
			protected LlmOrderSuggestionClient.Suggestion call() {
				return suggester.suggest(path, rows);
			}
		};
		// while it waits: how long, and "Stop" in place of "Suggest order" (it seemed to hang)
		long started = System.nanoTime();
		javafx.animation.Timeline ticker = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1),
				e -> status.setText("Asking the LLM for an order of " + rows.size() + " lines... " + (System.nanoTime() - started) / 1_000_000_000 + " s")));
		ticker.setCycleCount(javafx.animation.Animation.INDEFINITE);
		String suggestText = suggestButton.getText();
		javafx.event.EventHandler<javafx.event.ActionEvent> suggestAction = suggestButton.getOnAction();
		Runnable restore = () -> {
			ticker.stop();
			suggestButton.setText(suggestText);
			suggestButton.setOnAction(suggestAction);
		};
		suggestButton.setText("Stop");
		suggestButton.setOnAction(e -> {
			task.cancel(true);
			restore.run();
			status.setText("Stopped - the order is unchanged.");
		});
		status.setText("Asking the LLM for an order of " + rows.size() + " lines... 0 s");
		ticker.play();
		task.setOnSucceeded(e -> {
			restore.run();
			LlmOrderSuggestionClient.Suggestion suggestion = task.getValue();
			if (suggestion == null || !suggestion.succeeded()) {
				status.setText("No suggestion: " + (suggestion == null ? "no answer" : suggestion.failureReason()));
				return;
			}
			List<Integer> suggested = LlmOrderSuggestionClient.respecting(suggestion.order(), mustPrecede);
			List<ChildEntry> reordered = new ArrayList<>(rowData.subList(0, windowFrom));
			for (int i : suggested)
				reordered.add(window.get(i));
			reordered.addAll(rowData.subList(windowFrom + window.size(), rowData.size()));
			ChildEntry selected = table.getSelectionModel().getSelectedItem();
			rowData.setAll(reordered);
			if (selected != null)
				table.getSelectionModel().select(selected);
			table.refresh();
			afterReorder.run();
			status.setText("Suggested by the LLM - review it; OK records the order, Cancel leaves everything as it was.");
		});
		task.setOnFailed(e -> {
			restore.run();
			status.setText("No suggestion: " + task.getException());
		});
		// closing the dialog stops waiting
		this.setOnHidden(e -> {
			if (task.isRunning()) {
				task.cancel(true);
				ticker.stop();
			}
		});
		Thread thread = new Thread(task, "order-suggestion");
		thread.setDaemon(true);
		thread.start();
	}

	/** [a][b]: the order graph fixed row a before row b, directly or transitively. */
	private static boolean[][] mustPrecede(List<ChildEntry> rows, Order order) {
		int n = rows.size();
		boolean[][] precede = new boolean[n][n];
		for (int a = 0; a < n; a++) {
			// everything reachable from it comes after it
			java.util.BitSet after = order.after(rows.get(a).getNode());
			for (int b = 0; b < n; b++) {
				Integer node = order.nodeOf(rows.get(b).getNode());
				precede[a][b] = a != b && node != null && after.get(node);
			}
		}
		return precede;
	}

	/** The rows' nodes in their order, then the final newline, if the file has one. */
	private static List<Node.Op> withMarkers(List<ChildEntry> rows, List<Node.Op> markers) {
		List<Node.Op> nodes = rows.stream().map(ChildEntry::getNode).collect(Collectors.toList());
		nodes.addAll(markers);
		return nodes;
	}

	/**
	 * Whether swapping {@code earlier} and {@code later} (currently adjacent, in that relative order)
	 * would contradict a precedence relation the graph has already established, directly or
	 * transitively - {@code true} means don't allow it. Missing map entries (a child the graph has no
	 * matching node for at all) are treated as "no known constraint", same permissive fallback as
	 * having no graph at all.
	 */
	private static boolean swapBlocked(ChildEntry earlier, ChildEntry later, Order order) {
		return order.canReach(earlier.getNode(), later.getNode());
	}

	/**
	 * Whether the child currently at {@code idx} is entirely settled - can't move up (blocked or
	 * already first) and can't move down (blocked or already last) - i.e. no genuine ambiguity
	 * involves it. Used to visually de-emphasize such rows, so what's left un-dimmed is exactly the
	 * ambiguity the user actually needs to resolve.
	 */
	private static boolean isRowFixed(int idx, List<ChildEntry> rowData, Order order) {
		boolean canMoveUp = idx > 0 && !swapBlocked(rowData.get(idx - 1), rowData.get(idx), order);
		boolean canMoveDown = idx >= 0 && idx < rowData.size() - 1 && !swapBlocked(rowData.get(idx), rowData.get(idx + 1), order);
		return !canMoveUp && !canMoveDown;
	}

	/**
	 * Looks up a registered {@code AssociationInfoArtifactViewer} for {@code node}'s plugin (the same
	 * Guice multibinding + plugin-id lookup {@code ArtifactDetailView#getArtifactViewers} uses), and
	 * returns it ready to embed, or {@code null} if none is registered for this adapter (e.g. plain
	 * text) or the service isn't initialized. Association-highlighting info is irrelevant here (this
	 * dialog isn't the live Artifacts panel), so it's initialized with none.
	 */
	/** [source] on one line - its whitespace runs (line breaks, indentation) as one space - and shortened. */
	static String oneLine(String source) {
		String text = source.replaceAll("\\s+", " ").trim();
		return text.length() <= 200 ? text : text.substring(0, 199) + "\u2026";
	}

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

	/**
	 * What the order graph of a node's artifact says of the node's children, copied holding the
	 * service's lock: its nodes and edges, each child's node ({@link PartialOrderGraph#matchChildren})
	 * - asked on the FX thread while the dialog is open, as often as rows move.
	 */
	static final class Order {

		/** No graph: nothing is fixed. */
		private static final Order NONE = new Order(new IdentityHashMap<>(), new int[0][], new int[0], new boolean[0]);

		/** The graph node of each child that has one, by the child (the dialog's own nodes). */
		private final Map<Node, Integer> nodeOf;
		/** The graph's nodes' next nodes, by index. */
		private final int[][] next;
		private final int[] sequenceNumber;
		private final boolean[] hasArtifact;

		private Order(Map<Node, Integer> nodeOf, int[][] next, int[] sequenceNumber, boolean[] hasArtifact) {
			this.nodeOf = nodeOf;
			this.next = next;
			this.sequenceNumber = sequenceNumber;
			this.hasArtifact = hasArtifact;
		}

		/** Copies what the graph of [node]'s artifact says of its children - holding the service's lock. */
		static Order of(Node node) {
			PartialOrderGraph pog = node.getArtifact() != null ? node.getArtifact().getPartialOrderGraph() : null;
			if (pog == null)
				return NONE;
			Map<PartialOrderGraph.Node, Integer> index = new IdentityHashMap<>();
			List<PartialOrderGraph.Node> nodes = new ArrayList<>();
			java.util.Deque<PartialOrderGraph.Node> pending = new java.util.ArrayDeque<>();
			pending.push(pog.getHead());
			index.put(pog.getHead(), 0);
			nodes.add(pog.getHead());
			while (!pending.isEmpty())
				for (PartialOrderGraph.Node child : pending.pop().getNext())
					if (!index.containsKey(child)) {
						index.put(child, nodes.size());
						nodes.add(child);
						pending.push(child);
					}
			int[][] next = new int[nodes.size()][];
			int[] sequenceNumber = new int[nodes.size()];
			boolean[] hasArtifact = new boolean[nodes.size()];
			for (int i = 0; i < nodes.size(); i++) {
				PartialOrderGraph.Node pogNode = nodes.get(i);
				next[i] = pogNode.getNext().stream().mapToInt(index::get).toArray();
				sequenceNumber[i] = pogNode.getSequenceNumber();
				hasArtifact[i] = pogNode.getArtifact() != null;
			}
			// looked up by the children themselves: as matchChildren's map, an equal child finds the node too
			Map<Node, PartialOrderGraph.Node> matched = pog.matchChildren(node);
			Map<Node, Integer> nodeOf = new IdentityHashMap<>();
			for (Node child : node.getChildren()) {
				PartialOrderGraph.Node pogNode = matched.get(child);
				if (pogNode != null)
					nodeOf.put(child, index.get(pogNode));
			}
			return new Order(nodeOf, next, sequenceNumber, hasArtifact);
		}

		/**
		 * Whether the graph node of [later] can be reached from that of [earlier] - false if either has
		 * none. As {@link PartialOrderGraph.Op#canReach}, on the copy.
		 */
		boolean canReach(Node earlier, Node later) {
			Integer from = this.nodeOf.get(earlier), target = this.nodeOf.get(later);
			if (from == null || target == null)
				return false;
			java.util.Deque<Integer> stack = new java.util.ArrayDeque<>();
			java.util.BitSet stacked = new java.util.BitSet(this.next.length);
			stack.push(from);
			stacked.set(from);
			while (!stack.isEmpty()) {
				int current = stack.pop();
				// by sequence number only when it is assigned, as canReach does
				if (current == target
						|| (!this.hasArtifact[target] && !this.hasArtifact[current])
						|| (this.hasArtifact[target] && this.hasArtifact[current] && this.sequenceNumber[target] >= 0 && this.sequenceNumber[current] == this.sequenceNumber[target]))
					return true;
				for (int child : this.next[current])
					if (!stacked.get(child)) {
						stacked.set(child);
						stack.push(child);
					}
			}
			return false;
		}

		/** The index of [child]'s graph node, or null if it has none. */
		Integer nodeOf(Node child) {
			return this.nodeOf.get(child);
		}

		/** The indices of the graph nodes after that of [earlier], following the edges - none if it has no node. */
		java.util.BitSet after(Node earlier) {
			java.util.BitSet seen = new java.util.BitSet(this.next.length);
			Integer from = this.nodeOf.get(earlier);
			if (from == null)
				return seen;
			java.util.Deque<Integer> pending = new java.util.ArrayDeque<>();
			for (int child : this.next[from])
				pending.push(child);
			while (!pending.isEmpty()) {
				int current = pending.pop();
				if (seen.get(current))
					continue;
				seen.set(current);
				for (int child : this.next[current])
					pending.push(child);
			}
			return seen;
		}
	}

	private static class ChildEntry {
		private final Node.Op node;
		private final SimpleStringProperty label;
		/** The source text the row stands for, sent to the LLM (the label may add line numbers). */
		private final String text;

		ChildEntry(Node.Op node, String label, String text) {
			this.node = node;
			this.label = new SimpleStringProperty(label);
			this.text = text;
		}

		Node.Op getNode() {
			return this.node;
		}

		String getText() {
			return this.text;
		}

		SimpleStringProperty labelProperty() {
			return this.label;
		}
	}
}
