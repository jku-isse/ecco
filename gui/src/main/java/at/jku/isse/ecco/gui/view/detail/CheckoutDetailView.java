package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.io.DeleteDirectoryContentsDialog;
import at.jku.isse.ecco.gui.io.Directory;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.module.ModuleRevisions;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.ArtifactDiagnostics;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.util.Callback;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CheckoutDetailView extends BorderPane {

	private EccoService service;

	private Checkout currentCheckout;
	private Path currentBaseDir;

	final ObservableList<DiagnosticInfo> warningsData = FXCollections.observableArrayList();


	private Pane centerPane;
	private ToolBar toolBar;


	private TextField checkoutConfiguration;


	public CheckoutDetailView(EccoService service) {
		this.service = service;

		// toolbar
		this.toolBar = new ToolBar();
		this.setTop(toolBar);


		// details
		GridPane detailsPane = new GridPane();
		this.centerPane = detailsPane;
		detailsPane.setHgap(10);
		detailsPane.setVgap(10);
		detailsPane.setPadding(new Insets(10, 10, 10, 10));
		this.setCenter(this.centerPane);

		ColumnConstraints col1constraint = new ColumnConstraints();
		ColumnConstraints col2constraint = new ColumnConstraints();
		col2constraint.setFillWidth(true);
		col2constraint.setHgrow(Priority.ALWAYS);
		detailsPane.getColumnConstraints().addAll(col1constraint, col2constraint);

		RowConstraints emptyRowConstraint = new RowConstraints();
		RowConstraints heightRowConstraint = new RowConstraints();
		heightRowConstraint.setVgrow(Priority.ALWAYS);
		heightRowConstraint.setFillHeight(true);
		detailsPane.getRowConstraints().addAll(emptyRowConstraint, emptyRowConstraint, heightRowConstraint);


		this.checkoutConfiguration = new TextField();
		this.checkoutConfiguration.setEditable(false);

		int row = 0;
		detailsPane.add(new Label("Configuration: "), 1, row, 1, 1);
		row++;
		detailsPane.add(this.checkoutConfiguration, 1, row, 1, 1);
		row++;


		// list of missing/surplus module diagnostics
		TableView<DiagnosticInfo> warningsTable = new TableView<>();
		warningsTable.setEditable(false);
		warningsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<DiagnosticInfo, String> typeCol = new TableColumn<>("Type");
		typeCol.setPrefWidth(80);
		typeCol.setMinWidth(80);
		typeCol.setMaxWidth(80);
		typeCol.setResizable(false);

		TableColumn<DiagnosticInfo, String> moduleCol = new TableColumn<>("Module");
		moduleCol.setPrefWidth(200);

		TableColumn<DiagnosticInfo, String> traceCol = new TableColumn<>("Trace");
		traceCol.setPrefWidth(160);

		TableColumn<DiagnosticInfo, String> suggestedFixCol = new TableColumn<>("Suggested Fix");
		suggestedFixCol.setPrefWidth(300);

		TableColumn<DiagnosticInfo, Void> actionCol = new TableColumn<>("Action");
		actionCol.setPrefWidth(100);
		actionCol.setMinWidth(100);
		actionCol.setMaxWidth(100);
		actionCol.setResizable(false);
		actionCol.setCellFactory(column -> new TableCell<DiagnosticInfo, Void>() {
			private final Button applyButton = new Button("Apply Fix");
			{
				applyButton.setOnAction(event -> CheckoutDetailView.this.applyFix(getTableView().getItems().get(getIndex())));
			}

			@Override
			protected void updateItem(Void item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || getIndex() >= getTableView().getItems().size()) {
					setGraphic(null);
					return;
				}
				DiagnosticInfo info = getTableView().getItems().get(getIndex());
				setGraphic(info.getSuggestedConfigurationString() != null ? applyButton : null);
			}
		});

		warningsTable.getColumns().setAll(typeCol, moduleCol, traceCol, suggestedFixCol, actionCol);

		typeCol.setCellValueFactory((TableColumn.CellDataFeatures<DiagnosticInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getType()));
		moduleCol.setCellValueFactory((TableColumn.CellDataFeatures<DiagnosticInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getModule()));
		traceCol.setCellValueFactory((TableColumn.CellDataFeatures<DiagnosticInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getTrace()));
		suggestedFixCol.setCellValueFactory((TableColumn.CellDataFeatures<DiagnosticInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getSuggestedFix()));

		// wrap long text onto multiple lines (growing the row) instead of forcing the table -- and
		// the dialog window around it, via OperationView.fit() -- very wide.
		moduleCol.setCellFactory(wrappingCellFactory());
		traceCol.setCellFactory(wrappingCellFactory());
		suggestedFixCol.setCellFactory(wrappingCellFactory());

		warningsTable.setItems(this.warningsData);

		detailsPane.add(warningsTable, 1, row, 1, 1);
		row++;


		// show nothing initially
		this.showCheckout(null, null);
	}

	/**
	 * A {@link TableCell} that renders its text wrapped (width bound to the column), so long
	 * content grows the row taller instead of forcing the column -- and the table -- wider.
	 * {@link TableView#fixedCellSize} defaults to unset/0, which already allows per-row computed
	 * height, so no extra row-height wiring is needed for this to take effect.
	 */
	private static <S> Callback<TableColumn<S, String>, TableCell<S, String>> wrappingCellFactory() {
		return column -> new TableCell<S, String>() {
			private final Label label = new Label();

			{
				label.setWrapText(true);
				label.prefWidthProperty().bind(column.widthProperty().subtract(10));
			}

			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setGraphic(null);
				} else {
					label.setText(item);
					setGraphic(label);
				}
			}
		};
	}


	public final void showCheckout(Checkout checkout, Path baseDir) {
		this.currentCheckout = checkout;
		this.currentBaseDir = baseDir;

		this.warningsData.clear();

		if (checkout != null) {
			this.setCenter(this.centerPane);
			this.toolBar.setDisable(false);

			if (checkout.getConfiguration() != null)
				this.checkoutConfiguration.setText(String.valueOf(checkout.getConfiguration().toString()));
			else
				this.checkoutConfiguration.setText("");

			// show missing and surplus module diagnostics (see Repository.Op.compose())
			List<ModuleRevision> sortedMissing = new ArrayList<>(checkout.getMissing());
			sortedMissing.sort(ModuleRevisions.RELEVANCE_ORDER);
			for (ModuleRevision missingModuleRevision : sortedMissing) {
				String location = checkout.getMissingLocations().getOrDefault(missingModuleRevision, "");
				CheckoutDetailView.this.warningsData.add(new DiagnosticInfo("MISSING", ModuleRevisions.describe(missingModuleRevision), location,
						ModuleRevisions.suggestFix(missingModuleRevision, checkout.getConfiguration()),
						ModuleRevisions.suggestedConfigurationString(missingModuleRevision, checkout.getConfiguration())));
			}
			for (java.util.Map.Entry<ModuleRevision, String> surplusEntry : checkout.getSurplusModules().entrySet()) {
				CheckoutDetailView.this.warningsData.add(new DiagnosticInfo("SURPLUS", surplusEntry.getKey().toString(), surplusEntry.getValue(), "", null));
			}

			// show order diagnostics (see Repository.Op.compose()) -- the "fix" here reuses the exact
			// same Apply Fix flow as MISSING: no in-GUI reorder control, no writer/compose changes,
			// just point at the ambiguity and let the user reorder the checked-out file themselves.
			for (Artifact<?> orderWarningArtifact : checkout.getOrderWarnings()) {
				String suggestedConfigurationString = checkout.getConfiguration() != null
						? ModuleRevisions.suggestedConfigurationString(null, checkout.getConfiguration())
						: null;
				// one child per line (with its source line number(s), when the adapter tracked one)
				// instead of a comma-joined blob, so the wrapping cell reads as a real list.
				String childrenMultiline = String.join(System.lineSeparator(), ArtifactDiagnostics.describeChildrenWithLines(orderWarningArtifact));
				CheckoutDetailView.this.warningsData.add(new DiagnosticInfo("ORDER",
						orderWarningArtifact + " (current order):" + System.lineSeparator() + childrenMultiline,
						ArtifactDiagnostics.describePath(orderWarningArtifact),
						ArtifactDiagnostics.suggestOrderFix(),
						suggestedConfigurationString));
			}
		} else {
			this.setCenter(null);
			this.toolBar.setDisable(true);

			this.checkoutConfiguration.setText("");
		}
	}

	/**
	 * Prompts for a directory + configuration + commit message (pre-filled with the suggestion),
	 * commits it, then re-runs the original checkout in place to verify/refresh the diagnostics.
	 * ECCO can't synthesize the combined content itself -- this only automates the commit +
	 * re-checkout steps around content the user points at.
	 */
	private void applyFix(DiagnosticInfo diagnosticInfo) {
		// MISSING content usually comes from somewhere new (default: repository home dir); an ORDER
		// fix is reordering content that already exists at the checkout's own output directory.
		Path defaultDirectory = "ORDER".equals(diagnosticInfo.getType()) ? this.currentBaseDir : this.service.getRepositoryHomeDir();
		ApplyFixDialog dialog = new ApplyFixDialog(defaultDirectory, diagnosticInfo.getSuggestedConfigurationString());
		Optional<ApplyFixDialog.Result> resultOpt = dialog.showAndWait();
		if (resultOpt.isEmpty()) return;
		ApplyFixDialog.Result result = resultOpt.get();
		Path checkoutBaseDirToReplay = this.currentBaseDir;

		Task<Commit> commitTask = new Task<Commit>() {
			@Override
			public Commit call() {
				CheckoutDetailView.this.service.setBaseDir(result.getDirectory());
				return CheckoutDetailView.this.service.commit(result.getCommitMessage(), result.getConfigurationString());
			}

			@Override
			public void succeeded() {
				super.succeeded();
				CheckoutDetailView.this.rerunCheckoutAfterFix(checkoutBaseDirToReplay);
			}

			@Override
			public void failed() {
				super.failed();
				new ExceptionAlert(getException()).showAndWait();
			}

			@Override
			public void cancelled() {
				super.cancelled();
				new ExceptionAlert(getException()).showAndWait();
			}
		};
		new Thread(commitTask).start();
	}

	/**
	 * Re-runs the exact original checkout (same {@link Configuration} object, same output
	 * directory) after a successful Apply-Fix commit, so the diagnostics table reflects the repo's
	 * new content rather than the stale pre-fix result.
	 */
	private void rerunCheckoutAfterFix(Path checkoutBaseDir) {
		try {
			if (!Directory.isEmpty(checkoutBaseDir) && !new DeleteDirectoryContentsDialog(checkoutBaseDir).showBlocked()) {
				return;
			}
		} catch (IOException e) {
			new ExceptionAlert(e).showAndWait();
			return;
		}

		Configuration configurationToReplay = this.currentCheckout.getConfiguration();
		Task<Checkout> checkoutTask = new Task<Checkout>() {
			@Override
			public Checkout call() {
				CheckoutDetailView.this.service.setBaseDir(checkoutBaseDir);
				return CheckoutDetailView.this.service.checkout(configurationToReplay);
			}

			@Override
			public void succeeded() {
				super.succeeded();
				CheckoutDetailView.this.showCheckout(getValue(), checkoutBaseDir);
			}

			@Override
			public void failed() {
				super.failed();
				new ExceptionAlert(getException()).showAndWait();
			}

			@Override
			public void cancelled() {
				super.cancelled();
				new ExceptionAlert(getException()).showAndWait();
			}
		};
		new Thread(checkoutTask).start();
	}


	/**
	 * Directory to commit from, configuration to commit under (pre-filled with the suggestion, but
	 * editable), and a commit message -- the minimum needed to actually apply a suggested fix.
	 */
	private static class ApplyFixDialog extends Dialog<ApplyFixDialog.Result> {

		ApplyFixDialog(Path defaultDirectory, String suggestedConfigurationString) {
			setTitle("Apply Fix");
			setHeaderText("Commit content under this configuration, then re-run the checkout to verify.");
			getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

			GridPane gridPane = new GridPane();
			gridPane.setHgap(10);
			gridPane.setVgap(10);
			gridPane.setPadding(new Insets(10, 10, 10, 10));

			int row = 0;

			Label directoryLabel = new Label("Directory: ");
			gridPane.add(directoryLabel, 0, row, 1, 1);
			TextField directoryField = new TextField(defaultDirectory == null ? "" : defaultDirectory.toString());
			gridPane.add(directoryField, 1, row, 1, 1);
			Button browseButton = new Button("...");
			gridPane.add(browseButton, 2, row, 1, 1);
			row++;

			Label configurationLabel = new Label("Configuration: ");
			gridPane.add(configurationLabel, 0, row, 1, 1);
			TextField configurationField = new TextField(suggestedConfigurationString);
			gridPane.add(configurationField, 1, row, 2, 1);
			row++;

			Label commitMessageLabel = new Label("Commit Message: ");
			gridPane.add(commitMessageLabel, 0, row, 1, 1);
			TextField commitMessageField = new TextField();
			gridPane.add(commitMessageField, 1, row, 2, 1);
			row++;

			browseButton.setOnAction(event -> {
				DirectoryChooser directoryChooser = new DirectoryChooser();
				try {
					Path directory = Paths.get(directoryField.getText());
					if (Files.exists(directory) && Files.isDirectory(directory))
						directoryChooser.setInitialDirectory(directory.toFile());
				} catch (Exception ignored) {
				}
				File selectedDirectory = directoryChooser.showDialog(this.getDialogPane().getScene().getWindow());
				if (selectedDirectory != null) {
					directoryField.setText(selectedDirectory.toPath().toString());
				}
			});

			getDialogPane().setContent(gridPane);

			Node okButton = getDialogPane().lookupButton(ButtonType.OK);
			okButton.setDisable(true);
			Runnable validate = () -> okButton.setDisable(
					directoryField.getText().isBlank() || configurationField.getText().isBlank() || commitMessageField.getText().isBlank());
			directoryField.textProperty().addListener((observable, oldValue, newValue) -> validate.run());
			configurationField.textProperty().addListener((observable, oldValue, newValue) -> validate.run());
			commitMessageField.textProperty().addListener((observable, oldValue, newValue) -> validate.run());

			setResultConverter(buttonType -> buttonType != ButtonType.OK ? null :
					new Result(Paths.get(directoryField.getText()), configurationField.getText(), commitMessageField.getText()));
		}

		static final class Result {
			private final Path directory;
			private final String configurationString;
			private final String commitMessage;

			Result(Path directory, String configurationString, String commitMessage) {
				this.directory = directory;
				this.configurationString = configurationString;
				this.commitMessage = commitMessage;
			}

			Path getDirectory() {
				return this.directory;
			}

			String getConfigurationString() {
				return this.configurationString;
			}

			String getCommitMessage() {
				return this.commitMessage;
			}
		}
	}


	public static class DiagnosticInfo {
		private final String type;
		private final String module;
		private final String trace;
		private final String suggestedFix;
		private final String suggestedConfigurationString;

		public DiagnosticInfo(String type, String module, String trace, String suggestedFix, String suggestedConfigurationString) {
			this.type = type;
			this.module = module;
			this.trace = trace;
			this.suggestedFix = suggestedFix;
			this.suggestedConfigurationString = suggestedConfigurationString;
		}

		public String getType() {
			return this.type;
		}

		public String getModule() {
			return this.module;
		}

		public String getTrace() {
			return this.trace;
		}

		public String getSuggestedFix() {
			return this.suggestedFix;
		}

		public String getSuggestedConfigurationString() {
			return this.suggestedConfigurationString;
		}
	}

}
