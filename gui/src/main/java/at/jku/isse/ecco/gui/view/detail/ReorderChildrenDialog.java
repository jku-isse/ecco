package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.gui.TableColumns;
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
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collections;
import java.util.List;
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
 */
public class ReorderChildrenDialog extends Dialog<List<Node.Op>> {

	@Inject
	private Set<ArtifactViewer> artifactViewers;
	@Inject
	private Set<AssociationInfoArtifactViewer> associationInfoArtifactViewers;

	public ReorderChildrenDialog(EccoService service, Node.Op node) {
		setTitle("Reorder Children");
		setHeaderText("Choose the order for the children of: " + node.getArtifact());
		setResizable(true);
		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		List<? extends Node.Op> children = node.getChildren();
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

		TableView<ChildEntry> table = new TableView<>();
		table.setEditable(false);
		table.setItems(rowData);
		table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		table.setPrefWidth(420);
		table.setPrefHeight(320);

		TableColumn<ChildEntry, Integer> orderCol = new TableColumn<>("#");
		orderCol.setSortable(false);
		orderCol.setReorderable(false);
		orderCol.setCellFactory(col -> new TableCell<>() {
			@Override
			protected void updateItem(Integer item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty ? null : String.valueOf(getIndex() + 1));
			}
		});

		TableColumn<ChildEntry, String> labelCol = new TableColumn<>("Child");
		labelCol.setCellValueFactory(param -> param.getValue().labelProperty());
		labelCol.setCellFactory(TableColumns.wrappingCellFactory());

		table.getColumns().setAll(orderCol, labelCol);
		TableColumns.defaultWidth(orderCol, 36);
		TableColumns.growToFill(table, labelCol);

		Button moveUpButton = new Button("Move Up");
		Button moveDownButton = new Button("Move Down");
		moveUpButton.setDisable(true);
		moveDownButton.setDisable(true);

		// the code preview (if any) tracks whichever row is currently selected, so the user can see
		// exactly what they're moving, not just its plain-text label.
		Pane codePreview = findCodePreview(service, node);

		table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
			int idx = table.getSelectionModel().getSelectedIndex();
			moveUpButton.setDisable(idx <= 0);
			moveDownButton.setDisable(idx < 0 || idx >= rowData.size() - 1);
			if (newV != null && codePreview instanceof ArtifactViewer) {
				((ArtifactViewer) codePreview).showTree(newV.getNode());
			}
		});

		moveUpButton.setOnAction(e -> {
			int idx = table.getSelectionModel().getSelectedIndex();
			if (idx > 0) {
				Collections.swap(rowData, idx, idx - 1);
				table.getSelectionModel().select(idx - 1);
			}
		});
		moveDownButton.setOnAction(e -> {
			int idx = table.getSelectionModel().getSelectedIndex();
			if (idx >= 0 && idx < rowData.size() - 1) {
				Collections.swap(rowData, idx, idx + 1);
				table.getSelectionModel().select(idx + 1);
			}
		});

		VBox buttons = new VBox(10, moveUpButton, moveDownButton);
		buttons.setAlignment(Pos.TOP_CENTER);

		HBox tableAndButtons = new HBox(10, table, buttons);
		HBox.setHgrow(table, Priority.ALWAYS);

		if (codePreview != null) {
			SplitPane splitPane = new SplitPane(tableAndButtons, codePreview);
			splitPane.setOrientation(Orientation.HORIZONTAL);
			splitPane.setDividerPositions(0.4);
			splitPane.setPrefWidth(900);
			splitPane.setPrefHeight(480);
			getDialogPane().setContent(splitPane);
		} else {
			tableAndButtons.setPadding(new Insets(10));
			getDialogPane().setContent(tableAndButtons);
		}

		if (!rowData.isEmpty()) {
			table.getSelectionModel().selectFirst();
		}

		setResultConverter(buttonType -> buttonType != ButtonType.OK ? null :
				rowData.stream().map(ChildEntry::getNode).collect(Collectors.toList()));
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
