package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.gui.TaskFailures;
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
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reviews {@link ConstraintMiner} suggestions mined from committed configurations, inside the
 * Feature Model tab (see {@link FeaturesView}). Accepted and rejected suggestions are both stored in
 * the repository itself, so they travel with fork/pull/push and a rejected one is not re-proposed to
 * anyone -- see {@code EccoService#acceptConstraints}/{@code #rejectConstraints}.
 *
 * <p>One table, filtered to the pending, accepted or rejected suggestions - there were a table and
 * two lists, each with its own buttons. The thresholds are behind the Thresholds toggle; the table
 * follows them, the repository and the reviews on its own.
 *
 * <p>Per the mining epistemic contract (CONSTRAINT_MINING_DESIGN.md): "accept" here only records
 * that a human reviewed the suggestion and agrees with it -- accepted suggestions are advisory
 * bookkeeping only, not (and must not silently become) enforced feature-model constraints that
 * block commit/checkout.
 */
public class ConstraintSuggestionsView extends BorderPane implements EccoListener {

    enum Status {
        PENDING("Pending", "Mined from the committed configurations, not reviewed yet. Double-click one to accept it."),
        ACCEPTED("Accepted", "Reviewed and agreed with - advisory, not an enforced feature-model constraint. Trusted once it is mined at "
                + EccoService.ACCEPTED_CONSTRAINT_MIN_WITNESS + " witnesses."),
        REJECTED("Rejected", "Reviewed and rejected - not proposed again.");

        final String title;
        final String explanation;

        Status(String title, String explanation) {
            this.title = title;
            this.explanation = explanation;
        }
    }

    /**
     * A suggestion as reviewed: its signature ({@code KIND|a|b}), its status, what the miner says
     * about it now (null if it is not mined - pending ones always are) and, for an accepted one,
     * whether it is trusted.
     */
    record ReviewRow(String signature, Status status, ConstraintMiner.Suggestion suggestion, String trust) {
    }

    private final EccoService service;

    private final VBox top;
    private final Spinner<Integer> minWitnessSpinner;
    private final Spinner<Double> confidenceSpinner;

    /** Every reviewed and pending suggestion; the table shows those of {@link #status}. Package-visible for testing. */
    final ObservableList<ReviewRow> rows = FXCollections.observableArrayList();
    private final FilteredList<ReviewRow> shownRows = new FilteredList<>(rows, row -> row.status() == Status.PENDING);
    private Status status = Status.PENDING;
    private final Map<Status, ToggleButton> statusButtons = new java.util.EnumMap<>(Status.class);

    /** The relations the code suggests, by "feature\0other" - for the Code column; FX thread. */
    private Map<String, at.jku.isse.ecco.service.llm.ImportedFeatureTraces.RelationSuggestion> relations = Map.of();

    private final TableView<ReviewRow> table;
    private final TableColumn<ReviewRow, String> trustCol;
    /** Shown only when the code backs a suggestion up - empty, it took the room the others needed. */
    private final TableColumn<ReviewRow, String> codeCol;
    private final Button acceptButton = new Button("Accept");
    private final Button rejectButton = new Button("Reject");
    private final Button moveBackButton = new Button("Move back to pending");
    private final Label explanation = new Label();

    private volatile boolean tabVisible = true;

    /** Re-triggered after every accept/unaccept -- see {@link #acceptSelected}/{@link #undoAccepted}. */
    private final MinimizationResults minimizationResults;

    public ConstraintSuggestionsView(EccoService service, MinimizationResults minimizationResults) {
        this.service = service;
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
        // re-mine whenever either threshold changes
        this.minWitnessSpinner.valueProperty().addListener((obs, oldV, newV) -> refresh());
        this.confidenceSpinner.valueProperty().addListener((obs, oldV, newV) -> refresh());
        this.minWitnessSpinner.setPrefWidth(90);
        this.confidenceSpinner.setPrefWidth(90);

        // the filter: one table instead of a table and two lists
        ToggleGroup statusGroup = new ToggleGroup();
        HBox statusBox = new HBox();
        statusBox.getStyleClass().add("segmented-button-bar");
        for (Status s : Status.values()) {
            ToggleButton button = new ToggleButton(s.title);
            button.setToggleGroup(statusGroup);
            button.setTooltip(new Tooltip(s.explanation));
            button.setOnAction(e -> {
                // a click on the selected one would leave none selected
                button.setSelected(true);
                this.showStatus(s);
            });
            this.statusButtons.put(s, button);
            statusBox.getChildren().add(button);
        }
        this.statusButtons.get(Status.PENDING).setSelected(true);

        // the thresholds are for experts: shown on request
        HBox thresholds = new HBox(6,
                new Label("Min witness:"), minWitnessSpinner,
                new Label("Confidence:"), confidenceSpinner);
        thresholds.setAlignment(Pos.CENTER_LEFT);
        thresholds.setPadding(new Insets(4, 8, 4, 8));
        thresholds.setVisible(false);
        thresholds.setManaged(false);
        ToggleButton thresholdsButton = new ToggleButton("Thresholds");
        thresholdsButton.setTooltip(new Tooltip("Min witness: how often a suggestion had the chance to be violated. "
                + "Confidence: the share of those chances it held in."));
        thresholds.visibleProperty().bind(thresholdsButton.selectedProperty());
        thresholds.managedProperty().bind(thresholdsButton.selectedProperty());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        ToolBar toolBar = new ToolBar(statusBox, spacer, thresholdsButton);
        this.top = new VBox(toolBar, thresholds);
        this.setTop(this.top);

        // the table
        this.table = new TableView<>(this.shownRows);
        table.setTableMenuButtonVisible(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<ReviewRow, String> constraintCol = new TableColumn<>("Constraint");
        constraintCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(describe(p.getValue().signature())));
        TableColumn<ReviewRow, String> evidenceCol = new TableColumn<>("Holds in");
        evidenceCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(evidence(p.getValue().suggestion())));
        evidenceCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                ReviewRow row = empty || getTableRow() == null ? null : getTableRow().getItem();
                setTooltip(row == null || row.suggestion() == null ? null : new Tooltip(evidenceDetails(row.suggestion())));
            }
        });
        // whether the code backs a REQUIRES up: the feature's imported commits changed mostly code
        // the other feature's commits created (see FeaturesView#relationSuggestions)
        this.codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().suggestion() == null ? "" : codeEvidence(this.relations, p.getValue().suggestion())));
        this.trustCol = new TableColumn<>("Trust");
        // "not yet trusted", and why in the tooltip - sized by the short text
        trustCol.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().trust() == null ? "" : p.getValue().trust().split(":")[0]));
        trustCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                ReviewRow row = empty || getTableRow() == null ? null : getTableRow().getItem();
                setTooltip(row == null || row.trust() == null || !row.trust().contains(":") ? null : new Tooltip(row.trust()));
            }
        });

        // the miner's raw numbers, for whoever wants them (the table menu, top right)
        TableColumn<ReviewRow, String> confidenceCol = rawColumn("Confidence", s -> String.format("%.3f", s.confidence));
        TableColumn<ReviewRow, String> witnessCol = rawColumn("Witness", s -> String.valueOf(s.witness));
        TableColumn<ReviewRow, String> supportCol = rawColumn("Support", s -> String.format("%.3f", s.support));
        TableColumn<ReviewRow, String> violationsCol = rawColumn("Violations", s -> String.valueOf(s.counterExamples.size()));

        List<TableColumn<ReviewRow, String>> columns = List.of(constraintCol, evidenceCol, codeCol, trustCol, confidenceCol, witnessCol, supportCol, violationsCol);
        table.getColumns().setAll(columns);
        for (TableColumn<ReviewRow, String> column : columns)
            TableColumns.fitToContent(column, this.rows);
        TableColumns.growToFill(table, constraintCol);
        for (TableColumn<ReviewRow, String> raw : List.of(confidenceCol, witnessCol, supportCol, violationsCol))
            raw.setVisible(false);

        // double-click accepts a pending suggestion right away, without needing the Accept button
        table.setRowFactory(tv -> {
            TableRow<ReviewRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && row.getItem().status() == Status.PENDING) {
                    acceptSelected(List.of(row.getItem().suggestion()));
                }
            });
            return row;
        });
        table.setPlaceholder(new Label("None"));

        acceptButton.setOnAction(e -> acceptSelected(selected().stream().map(ReviewRow::suggestion).toList()));
        rejectButton.setOnAction(e -> rejectSelected(selected().stream().map(ReviewRow::suggestion).toList()));
        moveBackButton.setOnAction(e -> {
            List<String> signatures = selected().stream().map(ReviewRow::signature).toList();
            if (this.status == Status.ACCEPTED)
                undoAccepted(signatures);
            else
                undoRejected(signatures);
        });
        explanation.setWrapText(true);
        for (Button button : List.of(acceptButton, rejectButton, moveBackButton))
            button.setMinWidth(Region.USE_PREF_SIZE);
        explanation.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b6a65;");
        HBox.setHgrow(explanation, Priority.ALWAYS);

        HBox actions = new HBox(8, acceptButton, rejectButton, moveBackButton, explanation);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(6));

        this.setCenter(table);
        this.setBottom(actions);
        this.showStatus(Status.PENDING);

        service.addListener(this);
        Platform.runLater(() -> statusChangedEvent(service));
    }

    private static TableColumn<ReviewRow, String> rawColumn(String title, java.util.function.Function<ConstraintMiner.Suggestion, String> value) {
        TableColumn<ReviewRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().suggestion() == null ? "" : value.apply(p.getValue().suggestion())));
        return column;
    }

    private List<ReviewRow> selected() {
        return new ArrayList<>(table.getSelectionModel().getSelectedItems());
    }

    /** Shows the suggestions of [status], and the buttons that apply to them. Package-visible for testing. */
    void showStatus(Status status) {
        this.status = status;
        this.statusButtons.get(status).setSelected(true);
        this.table.getSelectionModel().clearSelection();
        this.shownRows.setPredicate(row -> row.status() == status);
        boolean pending = status == Status.PENDING;
        for (Button button : List.of(acceptButton, rejectButton)) {
            button.setVisible(pending);
            button.setManaged(pending);
        }
        moveBackButton.setVisible(!pending);
        moveBackButton.setManaged(!pending);
        trustCol.setVisible(status == Status.ACCEPTED);
        explanation.setText(status.explanation);
    }

    /** "REQUIRES B -> A", as constraints read everywhere ({@code Constraint.Kind#describe}). */
    static String describe(String signature) {
        ConstraintSuggestionPreferences.AcceptedConstraint parsed = ConstraintSuggestionPreferences.parseSignature(signature);
        return parsed == null ? signature : parsed.kind.describe(parsed.a, parsed.b);
    }

    /**
     * "3 of 4", under "Holds in": of the configurations in which it could have been violated (its
     * witness), in how many it was not; empty if it is not mined. Package-visible for testing.
     */
    static String evidence(ConstraintMiner.Suggestion suggestion) {
        if (suggestion == null)
            return "";
        int held = suggestion.witness - suggestion.counterExamples.size();
        return held + " of " + suggestion.witness;
    }

    private static String evidenceDetails(ConstraintMiner.Suggestion suggestion) {
        int held = suggestion.witness - suggestion.counterExamples.size();
        return "Holds in " + held + " of the " + suggestion.witness + " configurations that could violate it. "
                + (suggestion.isHard() ? "No configuration violates it." : suggestion.counterExamples.size() + " configuration(s) violate it (a near miss).")
                + "\nConfidence " + String.format("%.3f", suggestion.confidence)
                + ", support " + String.format("%.3f", suggestion.support) + " (the share of all configurations it concerns).";
    }

    /**
     * Accept persists real {@link Constraint}s in the repository, all in ONE transaction -- see
     * {@code EccoService#acceptConstraints}. Deliberately does NOT also call {@link #refresh()} here
     * (nor do the other review actions): accepting already fires a real
     * {@link at.jku.isse.ecco.service.listener.EccoListener} status-changed event through
     * {@code EccoService}, which this view's own {@link #statusChangedEvent} already reacts to by
     * calling {@link #refresh()} (and which {@code FeaturesView} reacts to on its own, since it's a
     * listener too).
     */
    private void acceptSelected(List<ConstraintMiner.Suggestion> suggestions) {
        if (suggestions.isEmpty()) return;
        service.acceptConstraints(suggestions);
        // the accepted-constraint set just changed, which minimization's feature-model reasoning
        // depends on -- re-run automatically. No-op if a run is already in progress (see MinimizationResults#run).
        minimizationResults.run();
    }

    /**
     * Stored in the repository in one transaction, like {@link #acceptSelected}, whose status event
     * refreshes this view. Withdraws an acceptance of the same suggestion, so minimization re-runs too.
     */
    private void rejectSelected(List<ConstraintMiner.Suggestion> suggestions) {
        if (suggestions.isEmpty()) return;
        service.rejectConstraints(suggestions);
        minimizationResults.run();
    }

    /** Batched, one transaction/event for the whole selection -- see {@link #acceptSelected}. */
    private void undoAccepted(List<String> signatures) {
        List<ConstraintSuggestionPreferences.AcceptedConstraint> parsed = parse(signatures);
        if (parsed.isEmpty()) return;
        service.unacceptConstraints(parsed);
        // see acceptSelected -- same reasoning, un-accepting also changes the accepted-constraint set.
        minimizationResults.run();
    }

    /** Batched, one transaction/event for the whole selection -- see {@link #acceptSelected}. */
    private void undoRejected(List<String> signatures) {
        List<ConstraintSuggestionPreferences.AcceptedConstraint> parsed = parse(signatures);
        if (parsed.isEmpty()) return;
        service.unrejectConstraints(parsed);
    }

    private static List<ConstraintSuggestionPreferences.AcceptedConstraint> parse(List<String> signatures) {
        List<ConstraintSuggestionPreferences.AcceptedConstraint> parsed = new ArrayList<>();
        for (String signature : signatures) {
            ConstraintSuggestionPreferences.AcceptedConstraint constraint = ConstraintSuggestionPreferences.parseSignature(signature);
            if (constraint != null) parsed.add(constraint);
        }
        return parsed;
    }

    /**
     * Counts refreshes: each runs in a task of its own, and one started earlier can finish later -
     * its result would replace the newer one (seen as a stale list after changing a threshold).
     */
    private final java.util.concurrent.atomic.AtomicInteger refreshes = new java.util.concurrent.atomic.AtomicInteger();

    private void refresh() {
        if (!service.isInitialized()) return;
        int refresh = this.refreshes.incrementAndGet();

        top.setDisable(true);
        int minWitness = minWitnessSpinner.getValue();
        double confidence = confidenceSpinner.getValue();

        Task<Void> refreshTask = new Task<>() {
            @Override
            protected Void call() {
                // rejections kept per machine by earlier versions (a no-op once they are moved)
                ConstraintSuggestionsView.this.service.moveLocalRejectionsIntoRepository();
                List<Set<String>> configs = ConfigurationBridge.readConfigurations(ConstraintSuggestionsView.this.service);
                List<ConstraintMiner.Suggestion> mined =
                        new ConstraintMiner(minWitness, confidence, null).mine(configs);

                Set<String> accepted = AcceptedConstraints.acceptedSignatures(
                        ConstraintSuggestionsView.this.service.getRepository().getConstraints());
                Set<String> rejected = AcceptedConstraints.acceptedSignatures(
                        ConstraintSuggestionsView.this.service.getRepository().getRejectedConstraints());

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
                Map<String, ConstraintMiner.Suggestion> lenientBySignature = new java.util.HashMap<>();
                for (ConstraintMiner.Suggestion suggestion : lenientMined) {
                    lenientBySignature.put(ConstraintSuggestionPreferences.signatureOf(suggestion), suggestion);
                }

                Map<String, at.jku.isse.ecco.service.llm.ImportedFeatureTraces.RelationSuggestion> codeRelations = new java.util.HashMap<>();
                for (at.jku.isse.ecco.service.llm.ImportedFeatureTraces.RelationSuggestion relation : FeaturesView.relationSuggestions(ConstraintSuggestionsView.this.service))
                    codeRelations.put(relation.feature() + "\0" + relation.other(), relation);
                // backed by the code first
                pending.sort(java.util.Comparator.comparing((ConstraintMiner.Suggestion p) -> codeEvidence(codeRelations, p).isEmpty()));

                List<ReviewRow> all = new ArrayList<>();
                for (ConstraintMiner.Suggestion suggestion : pending)
                    all.add(new ReviewRow(ConstraintSuggestionPreferences.signatureOf(suggestion), Status.PENDING, suggestion, null));
                for (String signature : accepted.stream().sorted().toList()) {
                    ConstraintMiner.Suggestion current = lenientBySignature.get(signature);
                    String trust = trusted.contains(signature) ? "trusted"
                            : current == null ? "not yet trusted: not currently reproducible"
                            : "not yet trusted: needs " + EccoService.ACCEPTED_CONSTRAINT_MIN_WITNESS + " witnesses, has " + current.witness;
                    all.add(new ReviewRow(signature, Status.ACCEPTED, current, trust));
                }
                for (String signature : rejected.stream().sorted().toList())
                    all.add(new ReviewRow(signature, Status.REJECTED, lenientBySignature.get(signature), null));

                Platform.runLater(() -> {
                    if (refresh != ConstraintSuggestionsView.this.refreshes.get())
                        return; // a newer refresh shows its own
                    ConstraintSuggestionsView.this.relations = codeRelations;
                    codeCol.setVisible(!codeRelations.isEmpty());
                    rows.setAll(all);
                    updateCounts();
                    top.setDisable(false);
                });
                return null;
            }
        };
        TaskFailures.reportAndReenable(refreshTask, top);
        new Thread(refreshTask).start();
    }

    /** "Pending (3)" - how many there are, on each filter button. */
    private void updateCounts() {
        for (Status s : Status.values()) {
            long count = rows.stream().filter(row -> row.status() == s).count();
            this.statusButtons.get(s).setText(s.title + " (" + count + ")");
        }
    }

    /**
     * For a REQUIRES the code backs up: "subfeature (67%)" or "requires (26%, 1 own file)" - what
     * share of A's changes lie in code B created, and as which relation; empty otherwise.
     * Package-visible for testing.
     */
    static String codeEvidence(Map<String, at.jku.isse.ecco.service.llm.ImportedFeatureTraces.RelationSuggestion> relations, ConstraintMiner.Suggestion suggestion) {
        if (suggestion.kind != at.jku.isse.ecco.core.Constraint.Kind.REQUIRES || suggestion.b == null)
            return "";
        at.jku.isse.ecco.service.llm.ImportedFeatureTraces.RelationSuggestion relation = relations.get(suggestion.a + "\0" + suggestion.b);
        if (relation == null)
            return "";
        String share = Math.round(relation.share() * 100) + "%";
        return relation.relation() == at.jku.isse.ecco.service.llm.ImportedFeatureTraces.Relation.SUBFEATURE
                ? "subfeature (" + share + ")"
                : "requires (" + share + ", " + relation.ownSources() + " own file" + (relation.ownSources() == 1 ? "" : "s") + ")";
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
                rows.clear();
                updateCounts();
                this.setDisable(true);
            });
        }
    }
}
