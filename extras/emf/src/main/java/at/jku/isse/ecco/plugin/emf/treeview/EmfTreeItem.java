package at.jku.isse.ecco.plugin.emf.treeview;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.ListView;
import javafx.scene.control.TreeItem;

public class EmfTreeItem extends TreeItem<EmfTreeNode> {

    /** Control if the children of this tree item has been loaded. */
    private boolean hasLoadedChildren = false;

    public EmfTreeItem(EmfTreeNode object) {
        super(object);
    }

    @Override
    public ObservableList<TreeItem<EmfTreeNode>> getChildren() {
        if (hasLoadedChildren == false) {
            super.getChildren().setAll(buildChildren(this));
        }
        return super.getChildren();
    }

    @Override
    public boolean isLeaf() {
        return getValue().isLeaf();
    }

    private ObservableList<TreeItem<EmfTreeNode>> buildChildren(EmfTreeItem emfTreeItem) {
        hasLoadedChildren = true;
        EmfTreeNode itemNode = emfTreeItem.getValue();
        ObservableList<TreeItem<EmfTreeNode>> children = FXCollections.observableArrayList();
        for (EmfTreeNode ch : itemNode.getChildren()) {
            children.add(new EmfTreeItem(ch));
        }
        return children;
    }
}