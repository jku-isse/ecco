package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.view.detail.ArtifactDetailView;
import at.jku.isse.ecco.gui.view.operation.OperationView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.beans.binding.When;
import javafx.beans.property.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import java.util.Collection;

public class CommitComparisonView extends OperationView implements EccoListener {

    final ObservableList<Tuple2<Association, Association>> comparisonData = FXCollections.observableArrayList();

    public CommitComparisonView(final EccoService service, Commit oldCommit, Commit newCommit) {

        //Toolbar
        ToolBar toolBar = new ToolBar();
        this.setTop(toolBar);

        CheckBox useSimplifiedLabelsCheckBox = new CheckBox("Use Simplified Labels");
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
        commitsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Tuple2<Association, Association>, String> oldId = new TableColumn<>("Id");
        TableColumn<Tuple2<Association, Association>, String> oldAssociation = new TableColumn<>("Condition");
        TableColumn<Tuple2<Association, Association>, String> newId = new TableColumn<>("Id");
        TableColumn<Tuple2<Association, Association>, String> newAssociation = new TableColumn<>("Condition");
        TableColumn<Tuple2<Association, Association>, String> oldCommitCol = new TableColumn<>("Commit: " + oldCommit.getId().substring(0, 9) + "...");        //Table Title
        TableColumn<Tuple2<Association, Association>, String> newCommitCol = new TableColumn<>("Commit: " + newCommit.getId().substring(0, 9) + "...");        //Table Title

        oldCommitCol.getColumns().addAll(oldId, oldAssociation);
        newCommitCol.getColumns().addAll(newId, newAssociation);
        commitsTable.getColumns().setAll(oldCommitCol, newCommitCol);

        oldId.setCellValueFactory((TableColumn.CellDataFeatures<Tuple2<Association, Association>, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue()._1 == null ? "" : param.getValue()._1.getId()));
        newId.setCellValueFactory((TableColumn.CellDataFeatures<Tuple2<Association, Association>, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue()._2 == null ? "" : param.getValue()._2.getId()));
        oldAssociation.setCellValueFactory((TableColumn.CellDataFeatures<Tuple2<Association, Association>, String> param) -> new When(useSimplifiedLabelsCheckBox.selectedProperty())
                .then(param.getValue()._1 == null ? "" : param.getValue()._1.computeCondition().getSimpleModuleRevisionConditionString())
                .otherwise(param.getValue()._1 == null ? "" : param.getValue()._1.computeCondition().getModuleRevisionConditionString()));
        newAssociation.setCellValueFactory((TableColumn.CellDataFeatures<Tuple2<Association, Association>, String> param) -> new When(useSimplifiedLabelsCheckBox.selectedProperty())
                .then(param.getValue()._2 == null ? "" : param.getValue()._2.computeCondition().getSimpleModuleRevisionConditionString())
                .otherwise(param.getValue()._2 == null ? "" : param.getValue()._2.computeCondition().getModuleRevisionConditionString()));


        fillCompasisonData(oldCommit, newCommit);
        commitsTable.setItems(comparisonData);

        //Detail view
        ArtifactDetailView artifactDetailView = new ArtifactDetailView(service);
        commitsTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            if (newValue != null) {
                artifactDetailView.showTree(newValue._1 != null ? ArtifactDetailView.findBestArtifact(artifactDetailView, newValue._1.getRootNode()) : ArtifactDetailView.findBestArtifact(artifactDetailView, newValue._2.getRootNode()));
            }
        });

        // add to split pane
        splitPane.getItems().addAll(commitsTable, artifactDetailView);


        service.addListener(this);

        if (!service.isInitialized()) {
            this.setDisable(true);
        }
    }

    private void fillCompasisonData(final Commit oldCommit, final Commit newCommit) {
        Collection<Association> oldAssosiation = oldCommit.getAssociations();
        Collection<Association> newAssosiation = newCommit.getAssociations();

        //TODO there has to be a nicer way than this
        for (Association a : newAssosiation) {
            if (oldAssosiation.contains(a)) {
                for (Association b : oldAssosiation) {
                    if (a.equals(b)) {
                        comparisonData.add(new Tuple2<>(a, b));
                        break;
                    }
                }
            } else {
                comparisonData.add(new Tuple2<>(a, null));
            }
        }
        for (Association c : oldAssosiation) {
            if (!newAssosiation.contains(c)) {
                comparisonData.add(new Tuple2<>(null, c));
            }
        }
    }

    private static class Tuple2<F, S> {
        public F _1;
        public S _2;

        public Tuple2(F _1, S _2) {
            this._1 = _1;
            this._2 = _2;
        }
    }
}
