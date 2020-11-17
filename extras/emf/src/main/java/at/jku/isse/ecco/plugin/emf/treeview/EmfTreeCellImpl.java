package at.jku.isse.ecco.plugin.emf.treeview;

import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Created by hhoyos on 31/05/2017.
 */
public class EmfTreeCellImpl extends TreeCell<EmfTreeNode> {

    public static PseudoClass AMBIGUOUS_FEATURE = PseudoClass.getPseudoClass("ambiguous-feature");
    public static PseudoClass AMBIGUOUS_FEATURE_RESOLVED = PseudoClass.getPseudoClass("ambiguous-feature-resolved");
    public static PseudoClass SELECTED_TREE = PseudoClass.getPseudoClass("selected-tree");
    public static PseudoClass SELECTED_VALUE = PseudoClass.getPseudoClass("selected-value");


    /** Ambiguous Cell Representation */
    final private GridPane gridPane = new GridPane();
    final private HBox hbox = new HBox(8);
    final private TextField ambiguousFeatureName = new TextField();

    /**
     * A cell can be reused!
     */
    public EmfTreeCellImpl() {
        ambiguousFeatureName.setEditable(false);
    }

    @Override
    protected void updateItem(EmfTreeNode item, boolean empty) {
        super.updateItem(item, empty);
        setEditable(false);
        hbox.getChildren().clear();
        gridPane.getChildren().clear();
        ambiguousFeatureName.setText(null);
        setGraphic(null);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            // TODO Check if there is a better way to remove state
            pseudoClassStateChanged(AMBIGUOUS_FEATURE, false);
            pseudoClassStateChanged(AMBIGUOUS_FEATURE_RESOLVED, false);
            pseudoClassStateChanged(SELECTED_VALUE, false);
        }
        else {
            //TODO Use css to grey out empty multiFeatureNodes
            // Ambiguous Features
            if (item instanceof EmfTreeNode.AmbiguousEmfTreeNode) {
                EmfTreeNode.AmbiguousEmfTreeNode ambItem = (EmfTreeNode.AmbiguousEmfTreeNode) item;
                if (ambItem.getSelection() == -1) {
                    pseudoClassStateChanged(AMBIGUOUS_FEATURE, true);
                } else {
                    pseudoClassStateChanged(AMBIGUOUS_FEATURE_RESOLVED, true);
                }
                createGridPane(ambItem);
                setGraphic(gridPane);
            } else {
                setText(item.getLabel());

            }
        }
    }

    private void createGridPane(EmfTreeNode.AmbiguousEmfTreeNode ambiguousEmfNode) {

        for (EmfTreeNode childEmfNode : ambiguousEmfNode.getChildren()) {
            int selectionIndex = 0;
            if (childEmfNode instanceof EmfTreeNode.SingleAttributeNode) {
                TextField text = getTextField(ambiguousEmfNode, (EmfTreeNode.SingleAttributeNode) childEmfNode, selectionIndex, hbox);
                if (ambiguousEmfNode.getSelection() != -1) {
                    if (selectionIndex == ambiguousEmfNode.getSelection()) {
                        text.pseudoClassStateChanged(SELECTED_VALUE, true);
                    }
                }
                hbox.getChildren().add(text);
                selectionIndex++;
            }
            else {
                TreeView<EmfTreeNode> treeView = getEmfTreeNodeTreeView(ambiguousEmfNode, childEmfNode, selectionIndex, hbox);
                if (selectionIndex == ambiguousEmfNode.getSelection()) {
                    treeView.pseudoClassStateChanged(SELECTED_VALUE, true);
                }
                hbox.getChildren().add(treeView);
            }
        }
        ambiguousFeatureName.setText(ambiguousEmfNode.getLabel());
//        gridPane.add(ambiguousFeatureName,0,0);
//        gridPane.add(hbox,0,1);
        gridPane.getChildren().add(ambiguousFeatureName);
        gridPane.getChildren().add(hbox);
    }

    private TextField getTextField(EmfTreeNode ambiguousEmfNode, EmfTreeNode.SingleAttributeNode childEmfNode, int selectionIndex, HBox hbox) {
        TextField text = new TextField();
        final Integer selection = selectionIndex;
        text.setEditable(false);
        text.setText(childEmfNode.getValue().toString());
        // Context menu to resolve ambiguity
        ContextMenu contextMenu = new ContextMenu();
        MenuItem selectOption = new MenuItem("Select this option");
        contextMenu.getItems().add(selectOption);
        selectOption.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent event) {
                // Set the option in the ambiguous node so we can create the EMF Resource
                ((EmfTreeNode.AmbiguousEmfTreeNode)ambiguousEmfNode).setSelection(selection);
                hbox.getParent().getParent().pseudoClassStateChanged(AMBIGUOUS_FEATURE_RESOLVED, true);
                text.pseudoClassStateChanged(SELECTED_VALUE, true);
                for (Node n : hbox.getChildren()) {
                    if (n != text) {
                        n.pseudoClassStateChanged(SELECTED_VALUE, false);
                    }
                }
            }
        });
        text.setOnContextMenuRequested(event -> contextMenu.show(text, event.getScreenX(), event.getScreenY()));
        return text;
    }

    private TreeView<EmfTreeNode> getEmfTreeNodeTreeView(EmfTreeNode ambiguousEmfNode, EmfTreeNode childEmfNode, int index, HBox hbox) {
        final TreeView<EmfTreeNode> treeView = new TreeView<>();
        //treeView.setEditable(false);
        final Integer selection = index;
        TreeItem<EmfTreeNode> root = new EmfTreeItem(childEmfNode);
        treeView.setRoot(root);
        treeView.setCellFactory(tree -> new EmfTreeCellImpl());
        //treeView.setCellFactory(tree -> this);
        // Context menu to resolve ambiguity
        ContextMenu contextMenu = new ContextMenu();
        MenuItem selectOption = new MenuItem("Select this option");
        contextMenu.getItems().add(selectOption);
        selectOption.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent event) {
                // Set the option in the ambiguous node so we can create the EMF Resource
                ((EmfTreeNode.AmbiguousEmfTreeNode)ambiguousEmfNode).setSelection(selection);
                hbox.getParent().getParent().pseudoClassStateChanged(AMBIGUOUS_FEATURE_RESOLVED, true);
                // FIXME How to resolve the opposite ambiguity (e.g. container) if it exists?
                treeView.pseudoClassStateChanged(SELECTED_TREE, true);
                for (Node n : hbox.getChildren()) {
                    if (n != treeView) {
                        n.pseudoClassStateChanged(SELECTED_TREE, false);
                    }
                }
            }
        });
        treeView.setOnContextMenuRequested(event -> contextMenu.show(treeView, event.getScreenX(), event.getScreenY()));
        return treeView;
    }

}
