package at.jku.isse.ecco.plugin.emf.treeview;

import javafx.scene.control.TreeCell;

/**
 * Created by hhoyos on 31/05/2017.
 */
public class EmfTreeCellImpl extends TreeCell<EmfTreeNode> {


    public EmfTreeCellImpl() {
    }

    @Override
    protected void updateItem(EmfTreeNode item, boolean empty) {
        super.updateItem(item, empty);
        setText(item == null ? "" : item.getLabel());
        setEditable(false);
        //TODO Use css to grey out empty multiFeatureNodes
    }

}
