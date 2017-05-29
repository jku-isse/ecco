package at.jku.isse.ecco.plugin.emf;

import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.tree.Node;
import javafx.collections.ObservableList;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import javax.inject.Inject;
import java.util.*;

/**
 * Created by hhoyos on 23/05/2017.
 */
public class EmfViewer extends BorderPane implements ArtifactViewer {

    private ResourceSet resourceSet;
    private TreeView<EObject> treeView;
    Map<EObject, TreeItem<EObject>> eObjectTreeItemMap;
    private Resource resource;
    private EmfReconstruct rc;

    @Inject
    public EmfViewer(ResourceSet resourceSet) {
        this.resourceSet = resourceSet;
        eObjectTreeItemMap = new HashMap<>();
    }

    @Override
    public String getPluginId() {
        return EmfPlugin.class.getName();
    }

    @Override
    public void showTree(Node node) {
        if (resource == null) {
            rc = new EmfReconstruct();
            // Get root node
            Node.Op root = null;
            do {
                root = (Node.Op) node.getParent();
            } while (!(root.getArtifact().getData() instanceof PluginArtifactData));
            resource = rc.reconstructResource(root, resourceSet);
            TreeItem<EObject> dummyRoot = new TreeItem<>();
            treeView = new TreeView<>(dummyRoot);
            for (EObject eObject : resource.getContents()) {
                TreeItem<EObject> item = new LazyTreeItem(eObject);
                eObjectTreeItemMap.put(eObject, item);
                dummyRoot.getChildren().add(item);
            }
            this.setLeft(treeView);
        }
        MultipleSelectionModel msm = treeView.getSelectionModel();
        EObject nodeEObject = rc.getEObjectForNode((Node.Op) node);
        // Go up until firstVisible
        EObject firstVisible= nodeEObject;
        List<EObject> branch = new ArrayList<>();
        branch.add(firstVisible);
        while (!eObjectTreeItemMap.containsKey(firstVisible)) {
            EObject eContainer = firstVisible.eContainer();
            branch.add(eContainer);
            firstVisible = eContainer;
        }
        if (firstVisible != nodeEObject) {
            // Expand branch
            Collections.reverse(branch);
            expandBranch(eObjectTreeItemMap.get(firstVisible), branch);
        }
        TreeItem<EObject> treeItem = eObjectTreeItemMap.get(nodeEObject);
        int row = treeView.getRow( treeItem );
        // Now the row can be selected.
        msm.select( row );
    }

    private void expandBranch(TreeItem<?> item, List<EObject> branch){
        if(item != null && !item.isLeaf()){
            item.setExpanded(true);
            for(TreeItem<?> child : item.getChildren()){
                Object childEObject = child.getValue();
                if (branch.contains(childEObject)) {
                    expandBranch(child, branch);
                }
            }
        }
    }

    /**
     * Taken from http://www.loop81.com/2011/11/javafx-20-mastering-treeview.html (last visited 23/05/2017)
     */
    private class LazyTreeItem extends TreeItem<EObject> {

        /** The depth of this tree item in the {@link TreeView}. */
//        private final int depth;

        /** Control if the children of this tree item has been loaded. */
        private boolean hasLoadedChildren = false;

        public LazyTreeItem(EObject eObject) {
            super(eObject);
//            this.depth = depth;
        }

        @Override
        public ObservableList<TreeItem<EObject>> getChildren() {
            if (hasLoadedChildren == false) {
                loadChildren();
            }
            return super.getChildren();
        }

        @Override
        public boolean isLeaf() {
            if (hasLoadedChildren == false) {
                loadChildren();
            }
            return super.getChildren().isEmpty();
        }

        private void loadChildren() {
            hasLoadedChildren = true;
            EObject eObject = this.getValue();
            EClass eClass = eObject.eClass();
            for (EReference ref : eClass.getEAllContainments()) {
                EObject value = (EObject) eObject.eGet(ref);
                LazyTreeItem valueItem = new LazyTreeItem(value);
                this.getChildren().add(valueItem);
            }
        }

//        /** Return the depth of this item within the {@link TreeView}.*/
//        public int getDepth() {
//            return depth;
//        }
    }



}
