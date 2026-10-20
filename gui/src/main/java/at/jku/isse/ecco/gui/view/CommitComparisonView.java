package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.adapter.ArtifactViewer;
import at.jku.isse.ecco.adapter.AssociationInfoArtifactViewer;
import at.jku.isse.ecco.gui.EccoListenerLifecycle;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.RepositoryAccess;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.gui.view.detail.ArtifactDetailView;
import at.jku.isse.ecco.gui.view.operation.OperationView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import javafx.beans.property.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class CommitComparisonView extends OperationView implements EccoListener {

    final ObservableList<Tuple2<Association, Association>> comparisonData = FXCollections.observableArrayList();

    /** Shown for a condition until it is computed. */
    static final String COMPUTING = "…";

    /** The plugins that have a viewer - found on the FX thread when first needed: the viewers are panes, a WebView among them. */
    private Set<String> viewerPluginIds;

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

        oldId.setCellValueFactory((TableColumn.CellDataFeatures<Tuple2<Association, Association>, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().id1));
        newId.setCellValueFactory((TableColumn.CellDataFeatures<Tuple2<Association, Association>, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().id2));
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

        commitsTable.setItems(comparisonData);
        // the rows, then their conditions, read holding the service's lock in the background: a commit
        // running meanwhile adds associations and changes their conditions (read on the FX thread, the
        // window froze while it ran)
        RepositoryAccess.read(service, "Commit Comparison", svc -> rows(oldCommit, newCommit), rows -> {
            comparisonData.setAll(rows);
            RepositoryAccess.read(service, "Commit Comparison conditions", svc -> {
                for (Tuple2<Association, Association> row : rows)
                    row.computeConditions();
                return rows;
            }, computed -> {
                commitsTable.refresh();
                commitsTable.sort();
            }, failure -> new ExceptionAlert(failure).show());
        }, failure -> new ExceptionAlert(failure).show());

        TableColumns.shortIds(oldId, commitsTable.getItems());
        TableColumns.shortIds(newId, commitsTable.getItems());
        TableColumns.growToFill(commitsTable, oldAssociation);
        TableColumns.growToFill(commitsTable, newAssociation);

        //Detail view
        ArtifactDetailView artifactDetailView = new ArtifactDetailView(service);
        int[] selected = {0}; // counts the selections: a node found for an earlier one is not shown
        commitsTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            if (newValue != null && service.isInitialized()) {
                int read = ++selected[0];
                Set<String> pluginIds = this.viewerPluginIds(service);
                Association association = newValue._1 != null ? newValue._1 : newValue._2;
                // the node to show found holding the service's lock, in the background: a commit
                // running meanwhile changes the association's tree
                RepositoryAccess.read(service, "Commit Comparison artifacts", svc -> firstWithViewer(association.getRootNode(), pluginIds), node -> {
                    if (read == selected[0])
                        artifactDetailView.showTree(node);
                }, failure -> new ExceptionAlert(failure).show());
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

    /** The rows: each association of the two commits, under the commit (or both) it belongs to - holding the service's lock. */
    private static List<Tuple2<Association, Association>> rows(final Commit oldCommit, final Commit newCommit) {
        Collection<Association> oldAssosiation = oldCommit.getAssociations();
        Collection<Association> newAssosiation = newCommit.getAssociations();
        // the same association object in both (associations are equal only to themselves); a list's
        // contains() for each made this quadratic
        Set<Association> inOld = Collections.newSetFromMap(new IdentityHashMap<>());
        inOld.addAll(oldAssosiation);
        Set<Association> inNew = Collections.newSetFromMap(new IdentityHashMap<>());
        inNew.addAll(newAssosiation);

        // _1 is shown under the old commit's columns, _2 under the new commit's
        List<Tuple2<Association, Association>> rows = new ArrayList<>();
        for (Association a : newAssosiation)
            rows.add(new Tuple2<>(inOld.contains(a) ? a : null, a));
        for (Association c : oldAssosiation)
            if (!inNew.contains(c))
                rows.add(new Tuple2<>(c, null));
        return rows;
    }

    /** As {@link ArtifactDetailView#findNodeWithArtifactViewerRec}, by the plugins that have a viewer - holding the service's lock. */
    private static Node firstWithViewer(Node node, Set<String> pluginIds) {
        for (Node child : node.getChildren()) {
            if (pluginIds.contains(ArtifactDetailView.getPluginId(child)))
                return child;
            Node found = firstWithViewer(child, pluginIds);
            if (found != null)
                return found;
        }
        return null;
    }

    /** The plugins with a viewer the detail view shows - on the FX thread, which the viewers are made on. */
    private Set<String> viewerPluginIds(EccoService service) {
        if (this.viewerPluginIds == null) {
            Viewers viewers = new Viewers();
            service.getInjector().injectMembers(viewers);
            Set<String> pluginIds = new HashSet<>();
            for (Set<? extends ArtifactViewer> set : List.of(viewers.artifactViewers, viewers.associationInfoArtifactViewers))
                for (ArtifactViewer viewer : set)
                    if (viewer.getPluginId() != null && viewer instanceof Pane) // as ArtifactDetailView picks them
                        pluginIds.add(viewer.getPluginId());
            this.viewerPluginIds = pluginIds;
        }
        return this.viewerPluginIds;
    }

    /** The viewers, as {@link ArtifactDetailView} has them injected. */
    private static class Viewers {
        @Inject(optional = true)
        Set<ArtifactViewer> artifactViewers = Set.of();
        @Inject(optional = true)
        Set<AssociationInfoArtifactViewer> associationInfoArtifactViewers = Set.of();
    }

    /** A row: the association under the older commit, the one under the newer, their ids, and their conditions once computed. */
    private static class Tuple2<F extends Association, S extends Association> {
        /** Only read holding the service's lock. */
        public F _1;
        public S _2;
        final String id1, id2;
        volatile String simple1 = COMPUTING, full1 = COMPUTING, simple2 = COMPUTING, full2 = COMPUTING;

        public Tuple2(F _1, S _2) {
            this._1 = _1;
            this._2 = _2;
            this.id1 = _1 == null ? "" : _1.getId();
            this.id2 = _2 == null ? "" : _2.getId();
        }

        /** In the background, holding the service's lock; the same association on both sides has its condition computed once. */
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
