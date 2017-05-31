package at.jku.isse.ecco.plugin.emf;

import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.plugin.artifact.ArtifactViewer;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import at.jku.isse.ecco.plugin.emf.data.EmfResourceData;
import at.jku.isse.ecco.plugin.emf.treeview.EmfTreeCellImpl;
import at.jku.isse.ecco.plugin.emf.treeview.EmfTreeNode;
import at.jku.isse.ecco.tree.Node;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.util.Callback;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.impl.BasicEObjectImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import javax.inject.Inject;
import java.util.*;

/**
 * Created by hhoyos on 23/05/2017.
 */
public class EmfViewer extends BorderPane implements ArtifactViewer {

    private ResourceSet resourceSet;
    private TreeView<EmfTreeNode> treeView;
    Map<EObject, TreeItem<EmfTreeNode>> eObjectTreeItemMap;
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
            TreeItem<EmfTreeNode> dummyRoot = new TreeItem<>();
            treeView = new TreeView<>(dummyRoot);
            for (EObject eObject : resource.getContents()) {
                EmfTreeNode eNode = new EmfTreeNode.EObjectNode(eObject);
                TreeItem<EmfTreeNode> item = new EmfTreeItem(eNode);
                eObjectTreeItemMap.put(eObject, item);
                dummyRoot.getChildren().add(item);
            }
            treeView.setShowRoot(false);
            // Cell factory
            treeView.setCellFactory(new Callback<TreeView<EmfTreeNode>, TreeCell<EmfTreeNode>>(){
                @Override
                public TreeCell<EmfTreeNode> call(TreeView<EmfTreeNode> tree) {
                    return new EmfTreeCellImpl();
                }
            });
        }
        this.setCenter(treeView);
        MultipleSelectionModel msm = treeView.getSelectionModel();
        TreeItem<EmfTreeNode> treeItem;
        if (node.getArtifact().getData() instanceof EmfResourceData) {  // If the node is for an EmfResourceData artifact, show from the root
            treeItem= treeView.getRoot();
        }
        else {      // if (node.getArtifact().getData() instanceof EmfResourceData)
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
            treeItem = eObjectTreeItemMap.get(nodeEObject);
        }
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

    private class EmfTreeItem extends TreeItem<EmfTreeNode> {

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
            if (itemNode instanceof EmfTreeNode.EObjectNode) {
                EmfTreeNode.EObjectNode eObjectNode = (EmfTreeNode.EObjectNode) itemNode;
                EObject eObject = eObjectNode.getEObject();
                EClass eClass = eObject.eClass();
                for (EStructuralFeature feature : eClass.getEAllStructuralFeatures()) {
                    // Want to keep al metamodel features + eContainer (a la modisco)
                    EmfTreeNode node;
                    if (feature.isMany()) {
                        node = new EmfTreeNode.MultiFeatureNode(feature, eObject);
                    }
                    else {
                        if (feature instanceof  EReference) {
                            node = new EmfTreeNode.SingleReferenceNode(feature, eObject);
                        }
                        else {
                            node = new EmfTreeNode.SingleFeatureNode(feature, eObject);
                        }
                    }
                    children.add(new EmfTreeItem(node));
                }
            }
            else if (itemNode instanceof EmfTreeNode.SingleReferenceNode) {
                EObject eObject = (EObject) ((EmfTreeNode.SingleReferenceNode) itemNode).getValue();
                EmfTreeNode node = new EmfTreeNode.EObjectNode(eObject);
                children.add(new EmfTreeItem(node));
            }
            else if (itemNode instanceof EmfTreeNode.MultiFeatureNode) {
                Object value = ((EmfTreeNode.MultiFeatureNode) itemNode).getValue();
                assert value instanceof EList;

                EClassifier eType = ((EmfTreeNode.MultiFeatureNode) itemNode).getFeature().getEType();
                if (eType instanceof EClass) {
                    for (EObject child : (EList<EObject>) value) {
                        EmfTreeNode node = new EmfTreeNode.EObjectNode(child);
                        children.add(new EmfTreeItem(node));
                    }
                }
                else if (eType instanceof EDataType) {  // Works for all data types?
                    for (Object child : (EList<Object>) value) {
                        EmfTreeNode node = new EmfTreeNode.PrimitiveValueNode(child);
                        children.add(new EmfTreeItem(node));
                    }
                }
            }
            return children;
        }

    }

}
