package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.gui.EditableSpinner;
import at.jku.isse.ecco.mining.ConfigurationBridge;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.mining.ConstraintSuggestionPreferences;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reviews {@link ConstraintMiner} suggestions mined from committed configurations, inside the
 * Feature Model tab (see {@link FeaturesView}). Accept/reject decisions are recorded via
 * {@link ConstraintSuggestionPreferences} so a signature is not re-proposed once reviewed.
 *
 * <p>Per the mining epistemic contract (CONSTRAINT_MINING_DESIGN.md): "accept" here only records
 * that a human reviewed the suggestion and agrees with it -- there is no persisted
 * requires/excludes/mandatory constraint type in ECCO's data model yet, so accepted suggestions
 * are not (and must not silently become) enforced feature-model constraints.
 */
public class ConstraintSuggestionsView extends BorderPane implements EccoListener {

    private final EccoService service;

    private final ToolBar toolBar;
    private final Spinner<Integer> minWitnessSpinner;
    private final Spinner<Double> confidenceSpinner;

    private final ObservableList<ConstraintMiner.Suggestion> pendingData = FXCollections.observableArrayList();
    private final ObservableList<String> acceptedData = FXCollections.observableArrayList();
    private final ObservableList<String> rejectedData = FXCollections.observableArrayList();

    private final TableView<ConstraintMiner.Suggestion> pendingTable;
    private final ListView<String> acceptedListView;
    private final ListView<String> rejectedListView;

    private volatile boolean tabVisible = true;

    /** Notified after any accept/reject/undo decision, so the feature graph can re-render. */
    private final Runnable onReviewChanged;

    public ConstraintSuggestionsView(EccoService service, Runnable onReviewChanged) {
        this.service = service;
        this.onReviewChanged = onReviewChanged;

        this.minWitnessSpinner = new EditableSpinner(1, 1000, 4);
        this.confidenceSpinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 1.0, 0.9, 0.05));
        this.confidenceSpinner.setEditable(true);
        // same workaround as EditableSpinner (which minWitnessSpinner already gets for free): a
        // plain editable Spinner does NOT commit typed text to valueProperty() until the field
        // loses focus, so without this, refresh() below never fires while the user is still typing
        // a new confidence value -- only after clicking away, which reads as "doesn't filter".
        this.confidenceSpinner.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            try {
                Double.parseDouble(newValue);
                this.confidenceSpinner.increment(0);
            } catch (NumberFormatException ignored) {
            }
        });
        // re-mine whenever either threshold changes, not just on the Refresh button -- otherwise
        // moving the spinner changes its displayed value but the (stale) table never reflects it
        // until the user separately clicks Refresh.
        this.minWitnessSpinner.valueProperty().addListener((obs, oldV, newV) -> refresh());
        this.confidenceSpinner.valueProperty().addListener((obs, oldV, newV) -> refresh());

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refresh());

        this.toolBar = new ToolBar();
        toolBar.getItems().setAll(
                new Label("Min witness: "), minWitnessSpinner,
                new Separator(),
                new Label("Confidence: "), confidenceSpinner,
                new Separator(),
                refreshButton);
        this.setTop(toolBar);

        // pending suggestions table
        this.pendingTable = new TableView<>();
        pendingTable.setTableMenuButtonVisible(true);
        pendingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ConstraintMiner.Suggestion, String> kindCol = new TableColumn<>("Kind");
        kindCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().kind.toString()));
        TableColumn<ConstraintMiner.Suggestion, String> aCol = new TableColumn<>("A");
        aCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().a));
        TableColumn<ConstraintMiner.Suggestion, String> bCol = new TableColumn<>("B");
        bCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().b == null ? "" : p.getValue().b));
        TableColumn<ConstraintMiner.Suggestion, String> hardCol = new TableColumn<>("Hard");
        hardCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().isHard() ? "yes" : "no (near-miss)"));
        TableColumn<ConstraintMiner.Suggestion, String> confidenceCol = new TableColumn<>("Confidence");
        confidenceCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(String.format("%.3f", p.getValue().confidence)));
        TableColumn<ConstraintMiner.Suggestion, String> witnessCol = new TableColumn<>("Witness");
        witnessCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(String.valueOf(p.getValue().witness)));
        TableColumn<ConstraintMiner.Suggestion, String> supportCol = new TableColumn<>("Support");
        supportCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(String.format("%.3f", p.getValue().support)));
        TableColumn<ConstraintMiner.Suggestion, String> violationsCol = new TableColumn<>("Violations");
        violationsCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(String.valueOf(p.getValue().counterExamples.size())));

        pendingTable.getColumns().setAll(List.of(kindCol, aCol, bCol, hardCol, confidenceCol, witnessCol, supportCol, violationsCol));
        pendingTable.setItems(pendingData);
        pendingTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // double-click accepts a suggestion right away, without needing the Accept button
        pendingTable.setRowFactory(tv -> {
            TableRow<ConstraintMiner.Suggestion> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    review(List.of(row.getItem()), ConstraintSuggestionPreferences::accept);
                }
            });
            return row;
        });

        Button acceptButton = new Button("Accept");
        acceptButton.setOnAction(e -> review(new ArrayList<>(pendingTable.getSelectionModel().getSelectedItems()), ConstraintSuggestionPreferences::accept));
        Button rejectButton = new Button("Reject");
        rejectButton.setOnAction(e -> review(new ArrayList<>(pendingTable.getSelectionModel().getSelectedItems()), ConstraintSuggestionPreferences::reject));

        HBox pendingActions = new HBox(8, acceptButton, rejectButton);
        pendingActions.setPadding(new javafx.geometry.Insets(6));

        BorderPane pendingPane = new BorderPane();
        pendingPane.setCenter(pendingTable);
        pendingPane.setBottom(pendingActions);

        // reviewed suggestions (accepted / rejected)
        this.acceptedListView = new ListView<>(acceptedData);
        this.rejectedListView = new ListView<>(rejectedData);
        this.acceptedListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        this.rejectedListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        this.acceptedListView.setCellFactory(lv -> new SignatureCell());
        this.rejectedListView.setCellFactory(lv -> new SignatureCell());

        Button unacceptButton = new Button("Move back to pending");
        unacceptButton.setOnAction(e -> undoSelected(acceptedListView));
        Button unrejectButton = new Button("Move back to pending");
        unrejectButton.setOnAction(e -> undoSelected(rejectedListView));

        Label acceptedLabel = new Label("Accepted (reviewed, not yet an enforced feature-model constraint)");
        acceptedLabel.setWrapText(true);
        Label rejectedLabel = new Label("Rejected (will not be re-proposed)");
        rejectedLabel.setWrapText(true);

        VBox reviewedPane = new VBox(6,
                acceptedLabel, acceptedListView, unacceptButton,
                new Separator(),
                rejectedLabel, rejectedListView, unrejectButton);
        reviewedPane.setPadding(new javafx.geometry.Insets(6));

        SplitPane splitPane = new SplitPane(pendingPane, reviewedPane);
        splitPane.setDividerPositions(0.65);
        this.setCenter(splitPane);

        service.addListener(this);
        Platform.runLater(() -> statusChangedEvent(service));
    }

    private void review(List<ConstraintMiner.Suggestion> suggestions, java.util.function.BiConsumer<Path, String> decide) {
        if (suggestions.isEmpty()) return;
        Path repositoryDir = service.getRepositoryDir();
        for (ConstraintMiner.Suggestion suggestion : suggestions) {
            decide.accept(repositoryDir, ConstraintSuggestionPreferences.signatureOf(suggestion));
        }
        refresh();
        if (onReviewChanged != null) onReviewChanged.run();
    }

    private void undoSelected(ListView<String> listView) {
        List<String> selected = new ArrayList<>(listView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;
        Path repositoryDir = service.getRepositoryDir();
        for (String signature : selected) {
            ConstraintSuggestionPreferences.clearDecision(repositoryDir, signature);
        }
        refresh();
        if (onReviewChanged != null) onReviewChanged.run();
    }

    private void refresh() {
        if (!service.isInitialized()) return;

        toolBar.setDisable(true);
        int minWitness = minWitnessSpinner.getValue();
        double confidence = confidenceSpinner.getValue();

        Task<Void> refreshTask = new Task<>() {
            @Override
            protected Void call() {
                List<Set<String>> configs = ConfigurationBridge.readConfigurations(ConstraintSuggestionsView.this.service);
                List<ConstraintMiner.Suggestion> mined =
                        new ConstraintMiner(minWitness, confidence, null).mine(configs);

                Path repositoryDir = ConstraintSuggestionsView.this.service.getRepositoryDir();
                Set<String> accepted = ConstraintSuggestionPreferences.getAccepted(repositoryDir);
                Set<String> rejected = ConstraintSuggestionPreferences.getRejected(repositoryDir);

                List<ConstraintMiner.Suggestion> pending = new ArrayList<>();
                for (ConstraintMiner.Suggestion suggestion : mined) {
                    String signature = ConstraintSuggestionPreferences.signatureOf(suggestion);
                    if (!accepted.contains(signature) && !rejected.contains(signature)) pending.add(suggestion);
                }

                Platform.runLater(() -> {
                    pendingData.setAll(pending);
                    acceptedData.setAll(accepted.stream().sorted().collect(Collectors.toList()));
                    rejectedData.setAll(rejected.stream().sorted().collect(Collectors.toList()));
                    toolBar.setDisable(false);
                });
                return null;
            }
        };
        new Thread(refreshTask).start();
    }

    /** Called by {@link FeaturesView} whenever the containing tab is selected/deselected. */
    public void setTabVisible(boolean tabVisible) {
        boolean becameVisible = tabVisible && !this.tabVisible;
        this.tabVisible = tabVisible;
        if (becameVisible && this.service.isInitialized()) {
            refresh();
        }
    }

    @Override
    public void statusChangedEvent(EccoService service) {
        if (service.isInitialized()) {
            Platform.runLater(() -> this.setDisable(false));
            if (this.tabVisible) refresh();
        } else {
            Platform.runLater(() -> {
                pendingData.clear();
                acceptedData.clear();
                rejectedData.clear();
                this.setDisable(true);
            });
        }
    }

    /**
     * Renders a raw {@code KIND|a|b} signature as {@code "KIND: a → b"} (or just {@code "KIND: a"}
     * for MANDATORY, which has no second feature). The list's backing data stays the raw signature
     * -- needed verbatim by {@link ConstraintSuggestionPreferences#clearDecision} -- only the cell's
     * displayed text is reformatted.
     */
    private static final class SignatureCell extends ListCell<String> {
        private static final String ARROW = " → "; // "→" = RIGHTWARDS ARROW (real symbol, not "-->")

        @Override
        protected void updateItem(String signature, boolean empty) {
            super.updateItem(signature, empty);
            if (empty || signature == null) {
                setText(null);
                return;
            }
            ConstraintSuggestionPreferences.AcceptedConstraint parsed = ConstraintSuggestionPreferences.parseSignature(signature);
            if (parsed == null) {
                setText(signature);
            } else if (parsed.b == null) {
                setText(parsed.kind + ": " + parsed.a);
            } else {
                setText(parsed.kind + ": " + parsed.a + ARROW + parsed.b);
            }
        }
    }
}
