package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.gui.EditableSpinner;
import at.jku.isse.ecco.gui.MinimizationResults;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.mining.AcceptedConstraints;
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
 * Feature Model tab (see {@link FeaturesView}). An accepted suggestion is persisted as a real
 * {@link Constraint} in the repository itself (travels with fork/pull/push); a rejected one is
 * recorded locally via {@link ConstraintSuggestionPreferences} (personal, per-machine, so it's not
 * re-proposed) -- see {@code EccoService#acceptConstraint}/{@code #unacceptConstraint}.
 *
 * <p>Per the mining epistemic contract (CONSTRAINT_MINING_DESIGN.md): "accept" here only records
 * that a human reviewed the suggestion and agrees with it -- accepted suggestions are advisory
 * bookkeeping only, not (and must not silently become) enforced feature-model constraints that
 * block commit/checkout.
 */
public class ConstraintSuggestionsView extends BorderPane implements EccoListener {

    private final EccoService service;

    private final ToolBar toolBar;
    private final Spinner<Integer> minWitnessSpinner;
    private final Spinner<Double> confidenceSpinner;

    private final ObservableList<ConstraintMiner.Suggestion> pendingData = FXCollections.observableArrayList();
    private final ObservableList<String> acceptedData = FXCollections.observableArrayList();
    private final ObservableList<String> rejectedData = FXCollections.observableArrayList();

    /**
     * signature -> " [trusted]" / " [not yet trusted -- ...]", read by {@link SignatureCell} for
     * {@link #acceptedListView} only. Written just before {@code acceptedData.setAll(...)} inside
     * {@link #refresh()}'s {@code Platform.runLater}, so by the time cells re-render it's current;
     * plain (not observable) since {@code acceptedData.setAll(...)} already forces the re-render.
     */
    private final java.util.Map<String, String> acceptedStatusSuffix = new java.util.HashMap<>();

    private final TableView<ConstraintMiner.Suggestion> pendingTable;
    private final ListView<String> acceptedListView;
    private final ListView<String> rejectedListView;

    private volatile boolean tabVisible = true;

    /** Notified after any accept/reject/undo decision, so the feature graph can re-render. */
    private final Runnable onReviewChanged;

    /** Re-triggered after every accept/unaccept -- see {@link #acceptSelected}/{@link #undoAccepted}. */
    private final MinimizationResults minimizationResults;

    public ConstraintSuggestionsView(EccoService service, Runnable onReviewChanged, MinimizationResults minimizationResults) {
        this.service = service;
        this.onReviewChanged = onReviewChanged;
        this.minimizationResults = minimizationResults;

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

        // this is where a human naturally is right after reviewing/accepting suggestions, so this
        // is where the (shared, single) minimize run is triggered from -- see MinimizationResults;
        // Associations/Artifacts only ever display its results, they don't trigger their own.
        Button minimizeButton = new Button("Minimize Presence Conditions");
        minimizeButton.setOnAction(e -> minimizationResults.run());
        minimizeButton.disableProperty().bind(minimizationResults.runningProperty());
        ProgressBar minimizeProgressBar = new ProgressBar(0);
        minimizeProgressBar.progressProperty().bind(minimizationResults.progressProperty());
        minimizeProgressBar.visibleProperty().bind(minimizationResults.runningProperty());

        this.toolBar = new ToolBar();
        toolBar.getItems().setAll(
                new Label("Min witness: "), minWitnessSpinner,
                new Separator(),
                new Label("Confidence: "), confidenceSpinner,
                new Separator(),
                refreshButton,
                new Separator(),
                minimizeButton, minimizeProgressBar);
        this.setTop(toolBar);

        // pending suggestions table
        this.pendingTable = new TableView<>();
        pendingTable.setTableMenuButtonVisible(true);

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

        // all 8 columns are short content with no natural long-text column to absorb extra space,
        // so none of them grow - a small trailing gutter reads better than forcing one to stretch
        for (TableColumn<ConstraintMiner.Suggestion, String> column : List.of(kindCol, aCol, bCol, hardCol, confidenceCol, witnessCol, supportCol, violationsCol)) {
            TableColumns.fitToContent(column, pendingData);
        }

        // double-click accepts a suggestion right away, without needing the Accept button
        pendingTable.setRowFactory(tv -> {
            TableRow<ConstraintMiner.Suggestion> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    acceptSelected(List.of(row.getItem()));
                }
            });
            return row;
        });

        Button acceptButton = new Button("Accept");
        acceptButton.setOnAction(e -> acceptSelected(new ArrayList<>(pendingTable.getSelectionModel().getSelectedItems())));
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
        this.acceptedListView.setCellFactory(lv -> new SignatureCell(this.acceptedStatusSuffix));
        this.rejectedListView.setCellFactory(lv -> new SignatureCell(null));

        Button unacceptButton = new Button("Move back to pending");
        unacceptButton.setOnAction(e -> undoAccepted(acceptedListView));
        Button unrejectButton = new Button("Move back to pending");
        unrejectButton.setOnAction(e -> undoRejected(rejectedListView));

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

    /**
     * Accept persists real {@link Constraint}s in the repository, all in ONE transaction -- see
     * {@code EccoService#acceptConstraints}. Deliberately does NOT also call {@link #refresh()} or
     * {@code onReviewChanged} here (unlike {@link #review}/{@link #undoRejected}, which stay local
     * to {@link ConstraintSuggestionPreferences} and never fire a real event): accepting already
     * fires a real {@link at.jku.isse.ecco.service.listener.EccoListener} status-changed event
     * through {@code EccoService}, which this view's own {@link #statusChangedEvent} already reacts
     * to by calling {@link #refresh()} (and which {@code FeaturesView} reacts to on its own, since
     * it's a listener too) -- calling either again here would just be a second, redundant re-scan of
     * the same accept.
     */
    private void acceptSelected(List<ConstraintMiner.Suggestion> suggestions) {
        if (suggestions.isEmpty()) return;
        service.acceptConstraints(suggestions);
        // the accepted-constraint set just changed, which minimization's feature-model reasoning
        // depends on -- re-run automatically instead of requiring a separate manual click. No-op if
        // a run is already in progress (see MinimizationResults#run).
        minimizationResults.run();
    }

    /** Reject stays local -- see {@code ConstraintSuggestionPreferences#reject}. */
    private void review(List<ConstraintMiner.Suggestion> suggestions, java.util.function.BiConsumer<Path, String> decide) {
        if (suggestions.isEmpty()) return;
        Path repositoryDir = service.getRepositoryDir();
        for (ConstraintMiner.Suggestion suggestion : suggestions) {
            decide.accept(repositoryDir, ConstraintSuggestionPreferences.signatureOf(suggestion));
        }
        refresh();
        if (onReviewChanged != null) onReviewChanged.run();
    }

    /** Batched, one transaction/event for the whole selection -- see {@link #acceptSelected}. */
    private void undoAccepted(ListView<String> listView) {
        List<String> selected = new ArrayList<>(listView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;
        List<ConstraintSuggestionPreferences.AcceptedConstraint> parsed = new ArrayList<>();
        for (String signature : selected) {
            ConstraintSuggestionPreferences.AcceptedConstraint constraint = ConstraintSuggestionPreferences.parseSignature(signature);
            if (constraint != null) parsed.add(constraint);
        }
        service.unacceptConstraints(parsed);
        // see acceptSelected -- same reasoning, un-accepting also changes the accepted-constraint set.
        minimizationResults.run();
    }

    private void undoRejected(ListView<String> listView) {
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
                Set<String> accepted = AcceptedConstraints.acceptedSignatures(
                        ConstraintSuggestionsView.this.service.getRepository().getConstraints());
                Set<String> rejected = ConstraintSuggestionPreferences.getRejected(repositoryDir);

                List<ConstraintMiner.Suggestion> pending = new ArrayList<>();
                for (ConstraintMiner.Suggestion suggestion : mined) {
                    String signature = ConstraintSuggestionPreferences.signatureOf(suggestion);
                    if (!accepted.contains(signature) && !rejected.contains(signature)) pending.add(suggestion);
                }

                // an accepted signature is only actually TRUSTED (used for surplus suppression /
                // constraint-violation warnings) once it re-mines hard at the fixed production
                // threshold -- see EccoService#acceptedSuggestions. Surface that here rather than
                // leaving "accepted but silently untrusted" invisible to the user.
                Set<String> trusted = ConstraintSuggestionsView.this.service
                        .acceptedSuggestions(ConstraintSuggestionsView.this.service.getRepository())
                        .stream().map(ConstraintSuggestionPreferences::signatureOf).collect(Collectors.toSet());
                List<ConstraintMiner.Suggestion> lenientMined = new ConstraintMiner(
                        1, EccoService.ACCEPTED_CONSTRAINT_CONFIDENCE, null).mine(configs);
                java.util.Map<String, ConstraintMiner.Suggestion> lenientBySignature = new java.util.HashMap<>();
                for (ConstraintMiner.Suggestion suggestion : lenientMined) {
                    lenientBySignature.put(ConstraintSuggestionPreferences.signatureOf(suggestion), suggestion);
                }
                java.util.Map<String, String> statusSuffix = new java.util.HashMap<>();
                for (String signature : accepted) {
                    if (trusted.contains(signature)) {
                        statusSuffix.put(signature, " [trusted]");
                    } else {
                        ConstraintMiner.Suggestion current = lenientBySignature.get(signature);
                        statusSuffix.put(signature, current == null
                                ? " [not yet trusted -- not currently reproducible]"
                                : " [not yet trusted -- needs " + EccoService.ACCEPTED_CONSTRAINT_MIN_WITNESS
                                        + " witnesses, has " + current.witness + "]");
                    }
                }

                Platform.runLater(() -> {
                    pendingData.setAll(pending);
                    acceptedStatusSuffix.clear();
                    acceptedStatusSuffix.putAll(statusSuffix);
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
            // fireStatusChangedEvent() can be invoked from a background thread (e.g. commit()'s
            // Task); refresh() touches JavaFX UI directly, so it must run on the FX thread, not
            // whatever thread fired the event.
            Platform.runLater(() -> {
                this.setDisable(false);
                if (this.tabVisible) refresh();
            });
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

        /** signature -> trust-status suffix (e.g. " [trusted]"); null for lists with no such concept
         * (rejected suggestions were never trusted to begin with). */
        private final java.util.Map<String, String> statusSuffixBySignature;

        SignatureCell(java.util.Map<String, String> statusSuffixBySignature) {
            this.statusSuffixBySignature = statusSuffixBySignature;
        }

        @Override
        protected void updateItem(String signature, boolean empty) {
            super.updateItem(signature, empty);
            if (empty || signature == null) {
                setText(null);
                return;
            }
            ConstraintSuggestionPreferences.AcceptedConstraint parsed = ConstraintSuggestionPreferences.parseSignature(signature);
            String base = parsed == null ? signature
                    : parsed.b == null ? parsed.kind + ": " + parsed.a
                    : parsed.kind + ": " + parsed.a + ARROW + parsed.b;
            String suffix = statusSuffixBySignature == null ? "" : statusSuffixBySignature.getOrDefault(signature, "");
            setText(base + suffix);
        }
    }
}
