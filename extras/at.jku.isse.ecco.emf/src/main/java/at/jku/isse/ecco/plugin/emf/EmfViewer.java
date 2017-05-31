package at.jku.isse.ecco.plugin.emf;

import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.plugin.emf.data.EmfResourceData;
import at.jku.isse.ecco.plugin.emf.util.ReflectiveItemProvider;
import at.jku.isse.ecco.tree.Node;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.FeatureMap;

import javax.inject.Inject;
import java.util.*;

/**
 * Created by hhoyos on 23/05/2017.
 */
public class EmfViewer extends BorderPane implements ArtifactViewer {

    private ResourceSet resourceSet;
    private TreeView<Object> treeView;
    Map<EObject, TreeItem<Object>> eObjectTreeItemMap;
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
        ArtifactData data = node.getArtifact().getData();
        // Initial tree nodes must be ignored
        if (data instanceof PluginArtifactData) {
            return;
        }
        if (resource == null) {
            rc = new EmfReconstruct();
            while (!(data instanceof EmfResourceData)) {
                node = node.getParent();
                data = node.getArtifact().getData();
            }
            resource = rc.reconstructResource((Node.Op) node, resourceSet);
            TreeItem<Object> dummyRoot = new TreeItem<>();
            treeView = new TreeView<Object>(dummyRoot);
            for (EObject eObject : resource.getContents()) {
                TreeItem<Object> item = new EmfTreeItem(eObject);
                eObjectTreeItemMap.put(eObject, item);
                dummyRoot.getChildren().add(item);
            }

        }
        this.setLeft(treeView);
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
        TreeItem<Object> treeItem = eObjectTreeItemMap.get(nodeEObject);
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

    private class EmfTreeItem extends TreeItem<Object> {

        /** An EMF item is a leaf if it is an EAttribute, or if it's EClass does not have any EStructuralFeatures  */
        private boolean isLeaf;

        /** Control if the children of this tree item has been loaded. */
        private boolean hasLoadedChildren = false;

        public EmfTreeItem(Object object) {
            super(object);
            if (object instanceof  EObject) {
                isLeaf = !((EObject) object).eClass().getEAllReferences().isEmpty();
            }
            else {
                isLeaf = true;
            }
        }

        @Override
        public ObservableList<TreeItem<Object>> getChildren() {
            if (hasLoadedChildren == false) {
                super.getChildren().setAll(buildChildren(this));
            }
            return super.getChildren();
        }

        @Override
        public boolean isLeaf() {
            return isLeaf;
        }

        private ObservableList<TreeItem<Object>> buildChildren(EmfTreeItem emfTreeItem) {
            hasLoadedChildren = true;
            EObject eObject = (EObject) emfTreeItem.getValue();
            EClass eClass = eObject.eClass();
            ObservableList<TreeItem<Object>> children = FXCollections.observableArrayList();
            for (EStructuralFeature eStructuralFeature : eClass.getEAllStructuralFeatures()) {
                // Want to keep al metamodel features + eContainer (a la modisco)
                Object value = eObject.eGet(eStructuralFeature);
                EmfTreeItem valueItem = new EmfTreeItem(value);
                children.add(valueItem);
            }
            return children;
        }

        @Override
        public String toString() {
            Object object = getValue();
            if (object instanceof EObject) {
                EObject eObject = (EObject)object;
                EClass eClass = eObject.eClass();
                String label = String.format("[%s]", ReflectiveItemProvider.format(
                        ReflectiveItemProvider.capName(eClass.getName()), ' '));
                EStructuralFeature feature = ReflectiveItemProvider.getLabelFeature(eClass);
                if (feature != null) {
                    Object value = eObject.eGet(feature);
                    if (value != null) {
                        return label + " " + value.toString();
                    }
                }
                return label;
            }
            else {
                return object.toString();
            }
        }

    }
}
