package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.gui.EccoListenerLifecycle;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.gui.view.detail.ArtifactDetailView;
import at.jku.isse.ecco.gui.view.operation.OperationView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.beans.property.*;
import javafx.concurrent.Task;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class CommitComparisonView extends OperationView implements EccoListener {

    final ObservableList<Tuple2<Association, Association>> comparisonData = FXCollections.observableArrayList();

    /** Shown for a condition until it is computed. */
    static final String COMPUTING = "\u2026";

    public CommitComparisonView(final EccoService service, Commit oldCommit, Commit newCommit) {

        //Toolbar
        ToolBar toolBar = new ToolBar();
        this.setTop(toolBar);

        // what it does, as in the Dependency Graph: "A" for "A.1"
        CheckBox useSimplifiedLabelsCheckBox = new CheckBox("Hide Revisions");
        useSimplifiedLabelsCheckBox.setTooltip(new Tooltip("Show the conditions without feature revisions: A instead of A.1"));
        toolBar.getItems().add(useSimplifiedLabelsCheckBox);
        useSimplifiedLabelsCheckBox.setSelected(true);

        toolBar.getItems().add(new Separator());

        //Pane
        SplitPane splitPane = new SplitPane();
        this.setCenter(splitPane);

        //Table
        TableView<Tuple2<Association, Association>> commitsTable = new TableView<>();
        commitsTable.setEditable(false);
        commitsTable.setTableMenuButtonVisible(true);

        TableColumn<Tuple2<Association, Association>, String> oldId = new TableColumn<>("Id");
        TableColumn<Tuple2<Association, Association>, String> oldAssociation = new TableColumn<>("Condition");
        TableColumn<Tuple2<Association, Association>, String> newId = new TableColumn<>("Id");
        TableColumn<Tuple2<Association, Association>, String> newAssociation = new TableColumn<>("Condition");
        TableColumn<Tuple2<Association, Association>, String> oldCommitCol = new TableColumn<>("Commit " + TableColumns.shortIds(oldCommit.getId()));        //Table Title
        TableColumn<Tuple2<Association, Association>, String> newCommitCol = new TableColumn<>("Commit " + TableColumns.shortIds(newCommit.getId()));        //Table Title

        oldCommitCol.getColumns().addAll(oldId, oldAssociation);
        newCommitCol.getColumns().addAll(newId, newAssociation);
        commitsTable.getColumns().setAll(oldCommitCol, newCommitCol);

        oldId.setCellValueFactory((TableColumn.CellDataFeatures<Tuple2<Association, Association>, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue()._1 == null ? "" : param.getValue()._1.getId()));
        newId.setCellValueFactory((TableColumn.CellDataFeatures<Tuple2<Association, Association>, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue()._2 == null ? "" : param.getValue()._2.getId()));
        // the conditions computed once, in the background: computed for every cell shown - both kinds
        // of label - and for every comparison of a sort, sorting by Condition took 10 s for tinycc
        oldAssociation.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue()._1 == null ? ""
                : useSimplifiedLabelsCheckBox.isSelected() ? param.getValue().simple1 : param.getValue().full1));
        newAssociation.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue()._2 == null ? ""
                : useSimplifiedLabelsCheckBox.isSelected() ? param.getValue().simple2 : param.getValue().full2));
        useSimplifiedLabelsCheckBox.selectedProperty().addListener((o, was, simplified) -> {
            commitsTable.refresh();
            commitsTable.sort();
        });

        fillCompasisonData(oldCommit, newCommit);
        commitsTable.setItems(comparisonData);
        List<Tuple2<Association, Association>> rows = List.copyOf(comparisonData);
        Task<Void> conditionsTask = new Task<>() {
            @Override
            protected Void call() {
                for (Tuple2<Association, Association> row : rows)
                    row.computeConditions();
                return null;
            }
        };
        conditionsTask.setOnSucceeded(event -> {
            commitsTable.refresh();
            commitsTable.sort();
        });
        at.jku.isse.ecco.gui.TaskFailures.reportAndReenable(conditionsTask);
        Thread conditionsThread = new Thread(conditionsTask, "Commit Comparison conditions");
        conditionsThread.setDaemon(true);
        conditionsThread.start();

        TableColumns.shortIds(oldId, commitsTable.getItems());
        TableColumns.shortIds(newId, commitsTable.getItems());
        TableColumns.growToFill(commitsTable, oldAssociation);
        TableColumns.growToFill(commitsTable, newAssociation);

        //Detail view
        ArtifactDetailView artifactDetailView = new ArtifactDetailView(service);
        commitsTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            if (newValue != null) {
                artifactDetailView.showTree(newValue._1 != null ? ArtifactDetailView.findNodeWithArtifactViewerRec(artifactDetailView, newValue._1.getRootNode()) : ArtifactDetailView.findNodeWithArtifactViewerRec(artifactDetailView, newValue._2.getRootNode()));
            }
        });

        // add to split pane
        splitPane.getItems().addAll(commitsTable, artifactDetailView);


        service.addListener(this);
        // opened as its own window, once per comparison - unregister this view and its embedded
        // detail view (which registered itself too) when that window closes
        EccoListenerLifecycle.removeWhenWindowHidden(this, service, this, artifactDetailView);

        if (!service.isInitialized()) {
            this.setDisable(true);
        }
    }

    private void fillCompasisonData(final Commit oldCommit, final Commit newCommit) {
        Collection<Association> oldAssosiation = oldCommit.getAssociations();
        Collection<Association> newAssosiation = newCommit.getAssociations();
        // the same association object in both (associations are equal only to themselves); a list's
        // contains() for each made this quadratic
        Set<Association> inOld = Collections.newSetFromMap(new IdentityHashMap<>());
        inOld.addAll(oldAssosiation);
        Set<Association> inNew = Collections.newSetFromMap(new IdentityHashMap<>());
        inNew.addAll(newAssosiation);

        // _1 is shown under the old commit's columns, _2 under the new commit's
        for (Association a : newAssosiation)
            comparisonData.add(new Tuple2<>(inOld.contains(a) ? a : null, a));
        for (Association c : oldAssosiation)
            if (!inNew.contains(c))
                comparisonData.add(new Tuple2<>(c, null));
    }

    /** A row: the association under the older commit, the one under the newer, and their conditions once computed. */
    private static class Tuple2<F extends Association, S extends Association> {
        public F _1;
        public S _2;
        volatile String simple1 = COMPUTING, full1 = COMPUTING, simple2 = COMPUTING, full2 = COMPUTING;

        public Tuple2(F _1, S _2) {
            this._1 = _1;
            this._2 = _2;
        }

        /** In the background; the same association on both sides has its condition computed once. */
        void computeConditions() {
            if (this._1 != null) {
                at.jku.isse.ecco.module.Condition condition = this._1.computeCondition();
                this.simple1 = condition.toDisplayString(false);
                this.full1 = condition.toDisplayString();
            }
            if (this._2 == this._1) {
                this.simple2 = this.simple1;
                this.full2 = this.full1;
            } else if (this._2 != null) {
                at.jku.isse.ecco.module.Condition condition = this._2.computeCondition();
                this.simple2 = condition.toDisplayString(false);
                this.full2 = condition.toDisplayString();
            }
        }
    }
}
