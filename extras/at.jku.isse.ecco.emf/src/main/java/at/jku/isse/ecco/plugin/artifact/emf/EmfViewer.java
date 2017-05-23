package at.jku.isse.ecco.plugin.artifact.emf;

import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.tree.Node;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import javax.inject.Inject;

/**
 * Created by hhoyos on 23/05/2017.
 */
public class EmfViewer extends BorderPane implements ArtifactViewer {

    private ResourceSet resourceSet;
    private TreeView<EObject> treeView;
    private Resource resource;

    @Inject
    public EmfViewer(ResourceSet resourceSet) {
        this.resourceSet = resourceSet;
    }

    @Override
    public String getPluginId() {
        return EmfPlugin.class.getName();
    }

    @Override
    public void showTree(Node node) {
        EmfReconstruct rc = new EmfReconstruct();
        resource = rc.reconstructResource((Node.Op) node, resourceSet);
        TreeItem<EObject> dummyRoot = new TreeItem<>();
        TreeView<EObject> tree = new TreeView<EObject> (dummyRoot);
        for (EObject eObject : resource.getContents()) {
            //TreeItem<EObject> item = createEObjectSubtree(eObject);
            TreeItem<EObject> item = new LazyTreeItem(eObject);
            dummyRoot.getChildren().add(item);
        }
        this.setLeft(tree);
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
